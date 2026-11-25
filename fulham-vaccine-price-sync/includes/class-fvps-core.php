<?php
/**
 * WordPress side: fetch source prices, scan/save posts, history log, rollback.
 */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class FVPS_Core {

	const OPT      = 'fvps_settings';
	const SCAN_OPT = 'fvps_last_scan';
	const SRC_KEY  = 'fvps_source_cache';

	public static function table() {
		global $wpdb;
		return $wpdb->prefix . 'fvps_log';
	}

	public static function install() {
		global $wpdb;
		require_once ABSPATH . 'wp-admin/includes/upgrade.php';
		$charset = $wpdb->get_charset_collate();
		$table   = self::table();
		dbDelta( "CREATE TABLE $table (
  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  run_id varchar(40) NOT NULL,
  run_type varchar(20) NOT NULL DEFAULT 'manual',
  post_id bigint(20) unsigned NOT NULL,
  post_title text NOT NULL,
  post_url text NOT NULL,
  occ_id varchar(255) NOT NULL,
  label varchar(190) NOT NULL DEFAULT '',
  source_name varchar(190) NOT NULL DEFAULT '',
  old_price varchar(20) NOT NULL,
  new_price varchar(20) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'applied',
  user_id bigint(20) unsigned NOT NULL DEFAULT 0,
  created_at datetime NOT NULL,
  rolled_back_at datetime DEFAULT NULL,
  PRIMARY KEY  (id),
  KEY run_id (run_id),
  KEY post_id (post_id)
) $charset;" );
		update_option( 'fvps_db_version', FVPS_VERSION );
	}

	public static function settings() {
		$d = array(
			'source_url' => 'https://fulhamtravelclinic.com/all-vaccinations/',
			'post_types' => array( 'page', 'post' ),
			'aliases'    => '',
			'auto_sync'  => 0,
		);
		$s = get_option( self::OPT, array() );
		return wp_parse_args( is_array( $s ) ? $s : array(), $d );
	}

	/* ---------------- source ---------------- */

	/** @return array|WP_Error list of source items */
	public static function source( $force = false ) {
		$s = self::settings();
		if ( ! $force ) {
			$c = get_transient( self::SRC_KEY );
			if ( is_array( $c ) && ! empty( $c['items'] ) && $c['url'] === $s['source_url'] ) {
				return $c;
			}
		}
		$r = wp_remote_get( $s['source_url'], array( 'timeout' => 30, 'user-agent' => 'Mozilla/5.0 (FVPS price sync)' ) );
		if ( is_wp_error( $r ) ) {
			return $r;
		}
		if ( 200 !== (int) wp_remote_retrieve_response_code( $r ) ) {
			return new WP_Error( 'fvps_http', 'Source page returned HTTP ' . wp_remote_retrieve_response_code( $r ) );
		}
		$items = FVPS_Engine::parse_source( wp_remote_retrieve_body( $r ) );
		if ( ! $items ) {
			return new WP_Error( 'fvps_parse', 'No prices found on the source page. Its layout may have changed.' );
		}
		$c = array( 'url' => $s['source_url'], 'time' => time(), 'items' => $items );
		set_transient( self::SRC_KEY, $c, HOUR_IN_SECONDS );
		return $c;
	}

	public static function resolver( array $items ) {
		$s = self::settings();
		return FVPS_Engine::build_resolver( $items, FVPS_Engine::parse_aliases( $s['aliases'] ) );
	}

	/* ---------------- posts ---------------- */

	public static function get_posts() {
		$s = self::settings();
		return get_posts( array(
			'post_type'        => $s['post_types'],
			'post_status'      => array( 'publish', 'draft', 'pending', 'private', 'future' ),
			'numberposts'      => -1,
			'orderby'          => 'ID',
			'order'            => 'ASC',
			'no_found_rows'    => true,
			'suppress_filters' => true,
		) );
	}

	/**
	 * Scan one post (and optionally modify + save it).
	 * @return array occurrences
	 */
	public static function process_post( $post, $res, $modify = null, $save = false ) {
		$occs     = array();
		$changed  = false;
		$page_ctx = FVPS_Engine::page_context( $post->post_title, $post->post_name, $res );

		$raw  = get_post_meta( $post->ID, '_elementor_data', true );
		$data = is_string( $raw ) && $raw !== '' ? json_decode( $raw, true ) : ( is_array( $raw ) ? $raw : null );

		if ( is_array( $data ) && $data ) {
			$changed = FVPS_Engine::scan_data( $data, 'el', $res, $page_ctx, $occs, $modify );
			if ( $changed && $save ) {
				wp_save_post_revision( $post->ID );
				update_post_meta( $post->ID, '_elementor_data', wp_slash( wp_json_encode( $data ) ) );
				self::after_save( $post->ID );
			}
		} else {
			$content = $post->post_content;
			$changed = FVPS_Engine::scan_data( $content, 'c', $res, $page_ctx, $occs, $modify );
			if ( $changed && $save ) {
				global $wpdb;
				wp_save_post_revision( $post->ID );
				$wpdb->update( $wpdb->posts, array( 'post_content' => $content ), array( 'ID' => $post->ID ) );
				self::after_save( $post->ID );
			}
		}
		return $occs;
	}

	private static function after_save( $post_id ) {
		clean_post_cache( $post_id );
		delete_post_meta( $post_id, '_elementor_element_cache' );
		delete_post_meta( $post_id, '_elementor_css' );
		if ( class_exists( '\Elementor\Plugin' ) && isset( \Elementor\Plugin::$instance->files_manager ) ) {
			\Elementor\Plugin::$instance->files_manager->clear_cache();
		}
		do_action( 'fvps_post_updated', $post_id );
	}

	/** Scan every post. Returns rows for the review screen. */
	public static function scan_all() {
		$src = self::source();
		if ( is_wp_error( $src ) ) {
			return $src;
		}
		$res  = self::resolver( $src['items'] );
		$rows = array();
		foreach ( self::get_posts() as $post ) {
			foreach ( self::process_post( $post, $res ) as $o ) {
				if ( $o['label'] === null ) {
					continue; // a price that is not tied to a vaccine
				}
				$status = 'unmapped';
				$new    = '';
				if ( $o['item'] ) {
					$new    = $o['item']['price'];
					$status = FVPS_Engine::num( $o['old'] ) === FVPS_Engine::num( $new ) ? 'same' : 'change';
				}
				$rows[] = array(
					'post_id'     => $post->ID,
					'post_title'  => $post->post_title,
					'url'         => get_permalink( $post ),
					'edit'        => get_edit_post_link( $post, 'raw' ),
					'occ_id'      => $o['id'],
					'label'       => $o['label'],
					'source_name' => $o['item'] ? $o['item']['name'] : '',
					'unit'        => $o['item'] ? $o['item']['unit'] : '',
					'old'         => $o['old'],
					'new'         => $new,
					'status'      => $status,
					'via'         => $o['via'],
				);
			}
		}
		update_option( self::SCAN_OPT, array( 'time' => time(), 'posts' => count( self::get_posts() ), 'rows' => $rows ), false );
		return $rows;
	}

	/* ---------------- apply / rollback ---------------- */

	/**
	 * @param array $targets rows (as produced by scan_all) to apply.
	 * @return array [run_id, applied, skipped]
	 */
	public static function apply( array $targets, $run_type = 'manual' ) {
		global $wpdb;
		$src = self::source();
		if ( is_wp_error( $src ) ) {
			return $src;
		}
		$res     = self::resolver( $src['items'] );
		$run_id  = gmdate( 'YmdHis' ) . '-' . wp_generate_password( 4, false );
		$by_post = array();
		foreach ( $targets as $t ) {
			if ( $t['status'] === 'change' && $t['new'] !== '' ) {
				$by_post[ $t['post_id'] ][ $t['occ_id'] ] = $t;
			}
		}
		$applied = 0;
		$skipped = 0;
		foreach ( $by_post as $pid => $map ) {
			$post = get_post( $pid );
			if ( ! $post ) {
				$skipped += count( $map );
				continue;
			}
			$done   = array();
			$modify = function ( $f ) use ( $map, &$done ) {
				if ( ! isset( $map[ $f['id'] ] ) ) {
					return null;
				}
				$t = $map[ $f['id'] ];
				if ( FVPS_Engine::num( $f['old'] ) !== FVPS_Engine::num( $t['old'] ) ) {
					return null; // page was edited since the scan
				}
				$done[ $f['id'] ] = $t;
				return $t['new'];
			};
			self::process_post( $post, $res, $modify, true );
			$skipped += count( $map ) - count( $done );
			foreach ( $done as $t ) {
				$wpdb->insert( self::table(), array(
					'run_id'      => $run_id,
					'run_type'    => $run_type,
					'post_id'     => $pid,
					'post_title'  => $t['post_title'],
					'post_url'    => $t['url'],
					'occ_id'      => $t['occ_id'],
					'label'       => $t['label'],
					'source_name' => $t['source_name'],
					'old_price'   => $t['old'],
					'new_price'   => $t['new'],
					'status'      => 'applied',
					'user_id'     => get_current_user_id(),
					'created_at'  => current_time( 'mysql' ),
				) );
				$applied++;
			}
		}
		return array( 'run_id' => $run_id, 'applied' => $applied, 'skipped' => $skipped );
	}

	/** Undo logged changes. $where = array( 'id' => n ) or array( 'run_id' => x ). */
	public static function rollback( array $where ) {
		global $wpdb;
		$t = self::table();
		if ( isset( $where['ids'] ) ) {
			$ids  = array_filter( array_map( 'intval', $where['ids'] ) );
			$logs = $ids ? $wpdb->get_results( "SELECT * FROM $t WHERE status='applied' AND id IN (" . implode( ',', $ids ) . ')' ) : array();
		} elseif ( isset( $where['id'] ) ) {
			$logs = $wpdb->get_results( $wpdb->prepare( "SELECT * FROM $t WHERE id=%d AND status='applied'", $where['id'] ) );
		} else {
			$logs = $wpdb->get_results( $wpdb->prepare( "SELECT * FROM $t WHERE run_id=%s AND status='applied'", $where['run_id'] ) );
		}
		$src = self::source();
		$res = is_wp_error( $src ) ? FVPS_Engine::build_resolver( array() ) : self::resolver( $src['items'] );
		$by  = array();
		foreach ( $logs as $l ) {
			$by[ $l->post_id ][ $l->occ_id ] = $l;
		}
		$ok = 0;
		$bad = 0;
		foreach ( $by as $pid => $map ) {
			$post = get_post( $pid );
			if ( ! $post ) {
				$bad += count( $map );
				continue;
			}
			$done   = array();
			$modify = function ( $f ) use ( $map, &$done ) {
				if ( ! isset( $map[ $f['id'] ] ) ) {
					return null;
				}
				$l = $map[ $f['id'] ];
				if ( FVPS_Engine::num( $f['old'] ) !== FVPS_Engine::num( $l->new_price ) ) {
					return null; // price no longer what we set
				}
				$done[ $f['id'] ] = $l;
				return $l->old_price;
			};
			self::process_post( $post, $res, $modify, true );
			$bad += count( $map ) - count( $done );
			foreach ( $done as $l ) {
				$wpdb->update( $t, array( 'status' => 'rolled_back', 'rolled_back_at' => current_time( 'mysql' ) ), array( 'id' => $l->id ) );
				$ok++;
			}
		}
		return array( 'restored' => $ok, 'failed' => $bad );
	}

	/** Scan + apply everything that differs. */
	public static function sync_all( $run_type = 'manual' ) {
		$rows = self::scan_all();
		if ( is_wp_error( $rows ) ) {
			return $rows;
		}
		return self::apply( array_filter( $rows, function ( $r ) {
			return $r['status'] === 'change';
		} ), $run_type );
	}

	public static function cron_sync() {
		$s = self::settings();
		if ( ! empty( $s['auto_sync'] ) ) {
			self::sync_all( 'auto' );
		}
	}
}
