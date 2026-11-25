<?php
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class FVPS_Admin {

	public static function init() {
		add_action( 'admin_menu', array( __CLASS__, 'menu' ) );
		foreach ( array( 'refresh', 'scan', 'apply', 'sync_all', 'rollback', 'save_settings', 'export' ) as $a ) {
			add_action( 'admin_post_fvps_' . $a, array( __CLASS__, 'do_' . $a ) );
		}
	}

	public static function menu() {
		add_menu_page( 'Vaccine Price Sync', 'Vaccine Price Sync', 'manage_options', 'fvps', array( __CLASS__, 'page' ), 'dashicons-update', 58 );
	}

	private static function guard() {
		if ( ! current_user_can( 'manage_options' ) ) {
			wp_die( 'Not allowed.' );
		}
		check_admin_referer( 'fvps' );
	}

	private static function back( $tab, $args = array() ) {
		wp_safe_redirect( add_query_arg( array_merge( array( 'page' => 'fvps', 'tab' => $tab ), $args ), admin_url( 'admin.php' ) ) );
		exit;
	}

	private static function form( $action, $inner, $extra = '' ) {
		return '<form method="post" action="' . esc_url( admin_url( 'admin-post.php' ) ) . '" ' . $extra . '>'
			. '<input type="hidden" name="action" value="fvps_' . esc_attr( $action ) . '">'
			. wp_nonce_field( 'fvps', '_wpnonce', true, false ) . $inner . '</form>';
	}

	/* ---------------- handlers ---------------- */

	public static function do_refresh() {
		self::guard();
		delete_transient( FVPS_Core::SRC_KEY );
		$s = FVPS_Core::source( true );
		self::back( 'sync', is_wp_error( $s ) ? array( 'err' => rawurlencode( $s->get_error_message() ) ) : array( 'msg' => 'refreshed' ) );
	}

	public static function do_scan() {
		self::guard();
		$r = FVPS_Core::scan_all();
		if ( is_wp_error( $r ) ) {
			self::back( 'sync', array( 'err' => rawurlencode( $r->get_error_message() ) ) );
		}
		self::back( 'review', array( 'msg' => 'scanned' ) );
	}

	public static function do_apply() {
		self::guard();
		$scan = get_option( FVPS_Core::SCAN_OPT );
		$sel  = isset( $_POST['sel'] ) ? array_map( 'intval', (array) $_POST['sel'] ) : array();
		$rows = array();
		if ( $scan && ! empty( $scan['rows'] ) ) {
			foreach ( $sel as $i ) {
				if ( isset( $scan['rows'][ $i ] ) ) {
					$rows[] = $scan['rows'][ $i ];
				}
			}
		}
		if ( ! $rows ) {
			self::back( 'review', array( 'err' => rawurlencode( 'Nothing selected.' ) ) );
		}
		$r = FVPS_Core::apply( $rows, 'manual' );
		if ( is_wp_error( $r ) ) {
			self::back( 'review', array( 'err' => rawurlencode( $r->get_error_message() ) ) );
		}
		delete_option( FVPS_Core::SCAN_OPT );
		self::back( 'history', array( 'run' => $r['run_id'], 'msg' => 'applied', 'n' => $r['applied'], 'skipped' => $r['skipped'] ) );
	}

	public static function do_sync_all() {
		self::guard();
		$r = FVPS_Core::sync_all( 'one-click' );
		if ( is_wp_error( $r ) ) {
			self::back( 'sync', array( 'err' => rawurlencode( $r->get_error_message() ) ) );
		}
		delete_option( FVPS_Core::SCAN_OPT );
		self::back( 'history', array( 'run' => $r['run_id'], 'msg' => 'applied', 'n' => $r['applied'], 'skipped' => $r['skipped'] ) );
	}

	public static function do_rollback() {
		self::guard();
		if ( ! empty( $_POST['log_id'] ) ) {
			$where = array( 'id' => (int) $_POST['log_id'] );
		} elseif ( ! empty( $_POST['ids'] ) ) {
			$where = array( 'ids' => (array) $_POST['ids'] );
		} elseif ( ! empty( $_POST['run_id'] ) ) {
			$where = array( 'run_id' => sanitize_text_field( wp_unslash( $_POST['run_id'] ) ) );
		} else {
			self::back( 'history', array( 'err' => rawurlencode( 'Nothing selected.' ) ) );
		}
		$r     = FVPS_Core::rollback( $where );
		self::back( 'history', array( 'msg' => 'rolledback', 'n' => $r['restored'], 'skipped' => $r['failed'] ) );
	}

	public static function do_save_settings() {
		self::guard();
		$types = isset( $_POST['post_types'] ) ? array_map( 'sanitize_key', (array) $_POST['post_types'] ) : array( 'page' );
		$new   = array(
			'source_url' => esc_url_raw( wp_unslash( $_POST['source_url'] ) ),
			'post_types' => $types,
			'aliases'    => sanitize_textarea_field( wp_unslash( $_POST['aliases'] ) ),
			'auto_sync'  => empty( $_POST['auto_sync'] ) ? 0 : 1,
		);
		update_option( FVPS_Core::OPT, $new );
		delete_transient( FVPS_Core::SRC_KEY );
		wp_clear_scheduled_hook( 'fvps_cron' );
		if ( $new['auto_sync'] ) {
			wp_schedule_event( time() + HOUR_IN_SECONDS, 'daily', 'fvps_cron' );
		}
		self::back( 'settings', array( 'msg' => 'saved' ) );
	}

	public static function do_export() {
		self::guard();
		global $wpdb;
		$t   = FVPS_Core::table();
		$rid = isset( $_POST['run_id'] ) ? sanitize_text_field( wp_unslash( $_POST['run_id'] ) ) : '';
		$rows = $rid
			? $wpdb->get_results( $wpdb->prepare( "SELECT * FROM $t WHERE run_id=%s ORDER BY id", $rid ), ARRAY_A )
			: $wpdb->get_results( "SELECT * FROM $t ORDER BY id", ARRAY_A );
		header( 'Content-Type: text/csv; charset=utf-8' );
		header( 'Content-Disposition: attachment; filename=vaccine-price-history-' . gmdate( 'Y-m-d' ) . '.csv' );
		$o = fopen( 'php://output', 'w' );
		fputcsv( $o, array( 'Date', 'Run', 'Type', 'Page', 'Page URL', 'Vaccine on page', 'Matched source vaccine', 'Old price', 'New price', 'Status', 'User' ) );
		foreach ( $rows as $r ) {
			$u = get_userdata( $r['user_id'] );
			fputcsv( $o, array( $r['created_at'], $r['run_id'], $r['run_type'], $r['post_title'], $r['post_url'], $r['label'], $r['source_name'], '£' . $r['old_price'], '£' . $r['new_price'], $r['status'], $u ? $u->user_login : 'auto' ) );
		}
		exit;
	}

	/* ---------------- screens ---------------- */

	public static function page() {
		$tab  = isset( $_GET['tab'] ) ? sanitize_key( $_GET['tab'] ) : 'sync';
		$tabs = array( 'sync' => 'Sync', 'review' => 'Review & apply', 'history' => 'History & rollback', 'settings' => 'Settings & mappings' );
		echo '<div class="wrap"><h1>Vaccine Price Sync</h1>';
		echo '<style>.fvps-ok{color:#1a7f37;font-weight:600}.fvps-warn{color:#b35900;font-weight:600}.fvps-bad{color:#b32d2e;font-weight:600}.fvps-card{background:#fff;border:1px solid #dcdcde;padding:14px 18px;margin:14px 0;max-width:1100px}.fvps-old{text-decoration:line-through;color:#8c8f94}.fvps-tbl td,.fvps-tbl th{vertical-align:middle}.fvps-row-same{opacity:.55}</style>';
		echo '<h2 class="nav-tab-wrapper">';
		foreach ( $tabs as $k => $l ) {
			printf( '<a class="nav-tab %s" href="%s">%s</a>', $tab === $k ? 'nav-tab-active' : '', esc_url( admin_url( 'admin.php?page=fvps&tab=' . $k ) ), esc_html( $l ) );
		}
		echo '</h2>';
		self::notices();
		call_user_func( array( __CLASS__, 'tab_' . ( isset( $tabs[ $tab ] ) ? $tab : 'sync' ) ) );
		echo '</div>';
	}

	private static function notices() {
		if ( ! empty( $_GET['err'] ) ) {
			echo '<div class="notice notice-error"><p>' . esc_html( rawurldecode( wp_unslash( $_GET['err'] ) ) ) . '</p></div>';
		}
		$m = isset( $_GET['msg'] ) ? sanitize_key( $_GET['msg'] ) : '';
		$n = isset( $_GET['n'] ) ? (int) $_GET['n'] : 0;
		$k = isset( $_GET['skipped'] ) ? (int) $_GET['skipped'] : 0;
		$text = array(
			'refreshed'  => 'Source prices refreshed.',
			'scanned'    => 'Scan complete. Review the changes below.',
			'saved'      => 'Settings saved.',
			'applied'    => "Done. $n price(s) updated" . ( $k ? ", $k skipped (page changed since the scan)." : '.' ) . ' Every change is recorded below and can be undone.',
			'rolledback' => "$n price(s) restored" . ( $k ? ", $k could not be restored (price was edited again since)." : '.' ),
		);
		if ( isset( $text[ $m ] ) ) {
			echo '<div class="notice notice-success"><p>' . esc_html( $text[ $m ] ) . '</p></div>';
		}
	}

	private static function tab_sync() {
		$src = FVPS_Core::source();
		$s   = FVPS_Core::settings();
		echo '<div class="fvps-card"><h2>1. Source prices</h2>';
		echo '<p>Source: <a href="' . esc_url( $s['source_url'] ) . '" target="_blank">' . esc_html( $s['source_url'] ) . '</a></p>';
		if ( is_wp_error( $src ) ) {
			echo '<p class="fvps-bad">' . esc_html( $src->get_error_message() ) . '</p>';
		} else {
			echo '<p class="fvps-ok">' . count( $src['items'] ) . ' prices loaded</p>';
			echo '<table class="widefat striped fvps-tbl" style="max-width:520px"><thead><tr><th>Vaccine</th><th>Price</th><th>Unit</th></tr></thead><tbody>';
			foreach ( $src['items'] as $it ) {
				printf( '<tr><td>%s</td><td>£%s</td><td>%s</td></tr>', esc_html( $it['name'] ), esc_html( $it['price'] ), esc_html( $it['unit'] ) );
			}
			echo '</tbody></table>';
		}
		echo self::form( 'refresh', '<p><button class="button">Refresh source prices</button></p>' );
		echo '</div>';

		if ( is_wp_error( $src ) ) {
			return;
		}
		echo '<div class="fvps-card"><h2>2. Update this website</h2>';
		echo '<p><b>Safe way:</b> scan first, review exactly what will change, then apply.</p>';
		echo self::form( 'scan', '<p><button class="button button-primary button-hero">Scan all pages &amp; review changes</button></p>' );
		echo '<hr><p><b>One click:</b> scan every page and update all prices that differ, without reviewing.</p>';
		echo self::form( 'sync_all', '<p><button class="button button-secondary">Update ALL prices now</button></p>', 'onsubmit="return confirm(\'Update every vaccine price on all pages now? You can undo this from History.\')"' );
		echo '</div>';
	}

	private static function tab_review() {
		$scan = get_option( FVPS_Core::SCAN_OPT );
		if ( ! $scan || empty( $scan['rows'] ) ) {
			echo '<p>No scan results yet. Go to <a href="' . esc_url( admin_url( 'admin.php?page=fvps&tab=sync' ) ) . '">Sync</a> and run a scan.</p>';
			return;
		}
		$rows = $scan['rows'];
		$cnt  = array( 'change' => 0, 'same' => 0, 'unmapped' => 0 );
		foreach ( $rows as $r ) {
			$cnt[ $r['status'] ]++;
		}
		echo '<p>Scanned ' . (int) $scan['posts'] . ' pages on ' . esc_html( wp_date( 'j M Y H:i', $scan['time'] ) ) . ': <b>' . $cnt['change'] . '</b> to change, ' . $cnt['same'] . ' already correct, ' . $cnt['unmapped'] . ' unmatched.</p>';
		echo '<label><input type="checkbox" id="fvps-showsame"> Show prices that are already correct</label>';

		$inner  = '<table class="widefat fvps-tbl"><thead><tr><td class="check-column"><input type="checkbox" id="fvps-all" checked></td><th>Page</th><th>Vaccine on page</th><th>Matched to (source)</th><th>Price</th><th>Found via</th></tr></thead><tbody>';
		foreach ( $rows as $i => $r ) {
			$cls = 'fvps-row-' . $r['status'];
			if ( $r['status'] === 'change' ) {
				$cell = '<span class="fvps-old">£' . esc_html( $r['old'] ) . '</span> → <b class="fvps-ok">£' . esc_html( $r['new'] ) . '</b>';
				$chk  = '<input type="checkbox" name="sel[]" class="fvps-sel" value="' . $i . '" checked>';
			} elseif ( $r['status'] === 'same' ) {
				$cell = '£' . esc_html( $r['old'] ) . ' <span class="fvps-ok">✓ same</span>';
				$chk  = '';
			} else {
				$cell = '£' . esc_html( $r['old'] ) . ' <span class="fvps-warn">no match</span>';
				$chk  = '';
			}
			$inner .= sprintf(
				'<tr class="%s" %s><th class="check-column">%s</th><td><a href="%s" target="_blank">%s</a> <a href="%s" target="_blank">(edit)</a></td><td>%s</td><td>%s<br><small>%s</small></td><td>%s</td><td>%s</td></tr>',
				esc_attr( $cls ),
				$r['status'] === 'same' ? 'style="display:none"' : '',
				$chk,
				esc_url( $r['url'] ),
				esc_html( $r['post_title'] ),
				esc_url( $r['edit'] ),
				esc_html( $r['label'] ),
				esc_html( $r['source_name'] ?: '—' ),
				esc_html( $r['unit'] ),
				$cell,
				$r['via'] === 'page' ? 'page title' : 'vaccine name'
			);
		}
		$inner .= '</tbody></table>';
		$inner .= '<p><button class="button button-primary button-hero" onclick="return confirm(\'Apply the selected price changes?\')">Apply selected changes</button></p>';
		echo self::form( 'apply', $inner );
		if ( $cnt['unmapped'] ) {
			echo '<p class="description">Rows marked “no match” have a vaccine name that is not on the source list. Add them under <a href="' . esc_url( admin_url( 'admin.php?page=fvps&tab=settings' ) ) . '">Settings &amp; mappings</a> (e.g. <code>Rabies prophylaxis = Rabies (pre-exposure)</code>) and scan again.</p>';
		}
		?>
		<script>
		(function(){
			var all=document.getElementById('fvps-all');
			all.addEventListener('change',function(){document.querySelectorAll('.fvps-sel').forEach(function(c){c.checked=all.checked;});});
			document.getElementById('fvps-showsame').addEventListener('change',function(e){document.querySelectorAll('.fvps-row-same').forEach(function(r){r.style.display=e.target.checked?'':'none';});});
		})();
		</script>
		<?php
	}

	private static function tab_history() {
		global $wpdb;
		$t    = FVPS_Core::table();
		$runs = $wpdb->get_results( "SELECT run_id, run_type, MIN(created_at) AS at, COUNT(*) AS n, SUM(status='applied') AS live, MAX(user_id) AS uid FROM $t GROUP BY run_id ORDER BY MIN(id) DESC LIMIT 50" );
		if ( ! $runs ) {
			echo '<p>No changes recorded yet.</p>';
			return;
		}
		echo self::form( 'export', '<p><button class="button">Download full history (CSV)</button></p>' );
		$open = isset( $_GET['run'] ) ? sanitize_text_field( wp_unslash( $_GET['run'] ) ) : $runs[0]->run_id;
		echo '<table class="widefat striped fvps-tbl" style="max-width:1100px"><thead><tr><th>Run</th><th>When</th><th>Type</th><th>By</th><th>Prices</th><th>Still applied</th><th></th></tr></thead><tbody>';
		foreach ( $runs as $r ) {
			$u = get_userdata( $r->uid );
			printf(
				'<tr><td><a href="%s">%s</a></td><td>%s</td><td>%s</td><td>%s</td><td>%d</td><td>%d</td><td>%s %s</td></tr>',
				esc_url( admin_url( 'admin.php?page=fvps&tab=history&run=' . rawurlencode( $r->run_id ) ) ),
				esc_html( $r->run_id ),
				esc_html( $r->at ),
				esc_html( $r->run_type ),
				esc_html( $u ? $u->user_login : 'auto' ),
				(int) $r->n,
				(int) $r->live,
				$r->live ? self::form( 'rollback', '<input type="hidden" name="run_id" value="' . esc_attr( $r->run_id ) . '"><button class="button" onclick="return confirm(\'Restore ALL old prices from this run?\')">Undo whole run</button>', 'style="display:inline"' ) : '',
				self::form( 'export', '<input type="hidden" name="run_id" value="' . esc_attr( $r->run_id ) . '"><button class="button-link">CSV</button>', 'style="display:inline"' )
			);
		}
		echo '</tbody></table>';

		$rows = $wpdb->get_results( $wpdb->prepare( "SELECT * FROM $t WHERE run_id=%s ORDER BY post_title, id", $open ) );
		echo '<h3>Run ' . esc_html( $open ) . '</h3>';
		echo self::form( 'rollback', '', 'id="fvps-bulk"' );
		echo '<p><button class="button" form="fvps-bulk" onclick="return confirm(\'Restore the selected prices to their previous values?\')">Restore selected to previous price</button> <small>Tick the prices that were changed by mistake, or use the button on a single row.</small></p>';
		echo '<table class="widefat striped fvps-tbl" style="max-width:1100px"><thead><tr><td class="check-column"><input type="checkbox" id="fvps-hall"></td><th>Page</th><th>Vaccine</th><th>Old price</th><th>New price</th><th>Status</th><th></th></tr></thead><tbody>';
		foreach ( $rows as $r ) {
			$live = $r->status === 'applied';
			printf(
				'<tr><th class="check-column">%s</th><td><a href="%s" target="_blank">%s</a></td><td>%s</td><td>£%s</td><td>£%s</td><td>%s</td><td>%s</td></tr>',
				$live ? '<input type="checkbox" class="fvps-hsel" name="ids[]" value="' . (int) $r->id . '" form="fvps-bulk">' : '',
				esc_url( $r->post_url ),
				esc_html( $r->post_title ),
				esc_html( $r->label ),
				esc_html( $r->old_price ),
				esc_html( $r->new_price ),
				$live ? '<span class="fvps-ok">applied</span>' : 'rolled back ' . esc_html( $r->rolled_back_at ),
				$live ? '<button class="button button-small" name="log_id" value="' . (int) $r->id . '" form="fvps-bulk" onclick="return confirm(\'Restore £' . esc_attr( $r->old_price ) . ' on this page?\')">Restore £' . esc_html( $r->old_price ) . '</button>' : ''
			);
		}
		echo '</tbody></table>';
		?>
		<script>
		(function(){var a=document.getElementById('fvps-hall');if(a)a.addEventListener('change',function(){document.querySelectorAll('.fvps-hsel').forEach(function(c){c.checked=a.checked;});});})();
		</script>
		<?php
	}

	private static function tab_settings() {
		$s     = FVPS_Core::settings();
		$types = get_post_types( array( 'public' => true ), 'objects' );
		unset( $types['attachment'] );
		$inner  = '<table class="form-table"><tr><th>Source URL</th><td><input class="regular-text" name="source_url" value="' . esc_attr( $s['source_url'] ) . '"><p class="description">Page that lists the up-to-date prices.</p></td></tr>';
		$inner .= '<tr><th>Scan these content types</th><td>';
		foreach ( $types as $k => $o ) {
			$inner .= '<label style="margin-right:14px"><input type="checkbox" name="post_types[]" value="' . esc_attr( $k ) . '" ' . checked( in_array( $k, $s['post_types'], true ), true, false ) . '> ' . esc_html( $o->labels->name ) . '</label>';
		}
		$inner .= '</td></tr>';
		$inner .= '<tr><th>Extra name mappings</th><td><textarea name="aliases" rows="8" class="large-text code" placeholder="Rabies prophylaxis = Rabies (pre-exposure)">' . esc_textarea( $s['aliases'] ) . '</textarea><p class="description">One per line: <code>name used on this site = exact vaccine name on the source page</code>. Names that are the same on both sites are matched automatically.</p></td></tr>';
		$inner .= '<tr><th>Daily auto-sync</th><td><label><input type="checkbox" name="auto_sync" value="1" ' . checked( ! empty( $s['auto_sync'] ), true, false ) . '> Automatically update prices once a day (changes are still logged and can be undone)</label></td></tr></table>';
		$inner .= '<p><button class="button button-primary">Save settings</button></p>';
		echo self::form( 'save_settings', $inner );

		echo '<h3>Built-in mappings</h3><table class="widefat striped" style="max-width:700px"><tbody>';
		foreach ( FVPS_Engine::default_aliases() as $a => $b ) {
			printf( '<tr><td>%s</td><td>→ %s</td></tr>', esc_html( $a ), esc_html( $b ) );
		}
		echo '</tbody></table>';
	}
}
