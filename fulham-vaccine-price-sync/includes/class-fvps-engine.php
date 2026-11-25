<?php
/**
 * Pure logic: parse the source price list, match vaccine names, detect and replace prices in a string.
 * No WordPress database access here, so it can be unit tested on its own.
 */
if ( ! defined( 'ABSPATH' ) && ! defined( 'FVPS_TESTING' ) ) {
	exit;
}

class FVPS_Engine {

	/** Text nodes allowed between a vaccine name and its price. */
	const CTX_LIMIT = 14;

	/** £ sign (raw or entity) followed by the number. Group 1 = the number only. */
	const PRICE_RE = '/(?:\xC2\xA3|&pound;|&#0*163;|&#x0*a3;)\s*(\d{1,4}(?:\.\d{1,2})?)/i';

	/** Elementor setting keys that never contain visible text. */
	const SKIP_KEYS = array( 'id', 'elType', 'widgetType', 'isInner', 'url', 'link', 'custom_css', 'html_tag', 'image', 'background_image' );

	/** Wellness label => source name. Anything not listed here is matched automatically by name. */
	public static function default_aliases() {
		return array(
			'Hepatitis A Paediatric'         => 'Hepatitis A (child)',
			'Hepatitis A Paed'               => 'Hepatitis A (child)',
			'Hepatitis B Paediatric'         => 'Hepatitis B (child)',
			'Hepatitis B Paed'               => 'Hepatitis B (child)',
			'Rabies prophylaxis'             => 'Rabies (pre-exposure)',
			'Tick-Borne Paed'                => 'Tick-borne encephalitis (child)',
			'Tick-Borne Paediatric'          => 'Tick-borne encephalitis (child)',
			'Diphtheria, Tetanus and Polio'  => 'Diphtheria, tetanus and polio booster',
			'Typhoid'                        => 'Typhoid injection',
			'Private Flu'                    => 'Flu vaccine',
			'Flu'                            => 'Flu vaccine',
			'Private COVID'                  => 'COVID-19 vaccine',
			'COVID-19'                       => 'COVID-19 vaccine',
			'Chicken Pox'                    => 'Chickenpox',
			'Pneumonia'                      => 'Pneumonia vaccine',
			'Pneumococcal'                   => 'Pneumonia vaccine',
		);
	}

	/** Normalise a name for comparison: lowercase, no brand marks / punctuation. */
	public static function norm( $s ) {
		$s = html_entity_decode( strip_tags( (string) $s ), ENT_QUOTES | ENT_HTML5, 'UTF-8' );
		$s = str_replace( array( "\xE2\x80\x99", "\xE2\x80\x98", "'", '®', '™', '©' ), '', $s );
		$s = strtolower( $s );
		$s = str_replace( '&', ' and ', $s );
		$s = preg_replace( '/[^a-z0-9]+/', ' ', $s );
		return trim( preg_replace( '/\s+/', ' ', $s ) );
	}

	public static function num( $s ) {
		return round( (float) str_replace( ',', '.', (string) $s ), 2 );
	}

	public static function format_price( $n ) {
		$n = (float) $n;
		return ( floor( $n ) == $n ) ? (string) (int) $n : number_format( $n, 2, '.', '' );
	}

	/**
	 * Parse the source "all vaccinations" page.
	 * Returns list of [ 'name', 'price' (digits string), 'unit' ].
	 */
	public static function parse_source( $html ) {
		$items = array();
		if ( ! class_exists( 'DOMDocument' ) || trim( (string) $html ) === '' ) {
			return $items;
		}
		$prev = libxml_use_internal_errors( true );
		$dom  = new DOMDocument();
		$dom->loadHTML( '<?xml encoding="UTF-8">' . $html );
		libxml_clear_errors();
		libxml_use_internal_errors( $prev );
		$xp  = new DOMXPath( $dom );
		$has = function ( $c ) {
			return "contains(concat(' ',normalize-space(@class),' '),' $c ')";
		};
		$clean = function ( $s ) {
			return trim( preg_replace( '/\s+/u', ' ', str_replace( "\xC2\xA0", ' ', $s ) ) );
		};
		$seen = array();
		foreach ( $xp->query( '//*[' . $has( 'ftc-pr-row' ) . ']' ) as $row ) {
			$n = $xp->query( './/*[' . $has( 'ftc-pr-name' ) . ']', $row )->item( 0 );
			$p = $xp->query( './/*[' . $has( 'ftc-pr-price' ) . ']', $row )->item( 0 );
			$u = $xp->query( './/*[' . $has( 'ftc-pr-unit' ) . ']', $row )->item( 0 );
			if ( ! $n || ! $p ) {
				continue;
			}
			$name = $clean( $n->textContent );
			if ( $name === '' || ! preg_match( '/(\d+(?:\.\d+)?)/', $p->textContent, $m ) ) {
				continue;
			}
			$key = self::norm( $name );
			if ( isset( $seen[ $key ] ) ) {
				continue;
			}
			$seen[ $key ] = true;
			$items[]      = array(
				'name'  => $name,
				'price' => self::format_price( $m[1] ),
				'unit'  => $u ? $clean( $u->textContent ) : '',
			);
		}
		return $items;
	}

	/** Build a lookup used to resolve wellness-site names to source items. */
	public static function build_resolver( array $source, array $custom_aliases = array() ) {
		$index = array();
		foreach ( $source as $it ) {
			$keys   = array( self::norm( $it['name'] ) );
			$no_par = trim( preg_replace( '/\((?![^)]*(child|children|paed|pediatric))[^)]*\)/i', ' ', $it['name'] ) );
			$keys[] = self::norm( $no_par );
			foreach ( $keys as $k ) {
				if ( $k !== '' && ! isset( $index[ $k ] ) ) {
					$index[ $k ] = $it;
				}
			}
		}
		$aliases = array();
		foreach ( array_merge( self::default_aliases(), $custom_aliases ) as $from => $to ) {
			$tk = self::norm( $to );
			if ( isset( $index[ $tk ] ) ) {
				$aliases[ self::norm( $from ) ] = $index[ $tk ];
			}
		}
		return array( 'index' => $index, 'aliases' => $aliases );
	}

	public static function resolve( $res, $name ) {
		$k = self::norm( $name );
		if ( $k === '' ) {
			return null;
		}
		if ( isset( $res['aliases'][ $k ] ) ) {
			return $res['aliases'][ $k ];
		}
		return isset( $res['index'][ $k ] ) ? $res['index'][ $k ] : null;
	}

	/** Parse "Wellness name = Source name" lines. */
	public static function parse_aliases( $text ) {
		$out = array();
		foreach ( preg_split( '/\R/', (string) $text ) as $line ) {
			if ( strpos( $line, '=' ) === false ) {
				continue;
			}
			list( $a, $b ) = array_map( 'trim', explode( '=', $line, 2 ) );
			if ( $a !== '' && $b !== '' ) {
				$out[ $a ] = $b;
			}
		}
		return $out;
	}

	/** Vaccine implied by the page itself (title / slug) for pages that only say "Price: £95". */
	public static function page_context( $title, $slug, $res ) {
		$tries = array(
			$title,
			preg_replace( '/\b(private|vaccination|vaccinations|vaccine|injection|jab)\b/i', ' ', $title ),
			str_replace( '-', ' ', $slug ),
		);
		foreach ( $tries as $t ) {
			$it = self::resolve( $res, $t );
			if ( $it ) {
				return array( 'label' => $title, 'item' => $it );
			}
		}
		return null;
	}

	private static function is_noise( $d ) {
		if ( ! preg_match( '/[A-Za-z]/', $d ) ) {
			return true;
		}
		if ( preg_match( '#^(https?:|/|\#)#', $d ) ) {
			return true;
		}
		return ! preg_match( '/\s/', $d ) && $d === strtolower( $d );
	}

	public static function new_state() {
		return array( 'ctx' => null, 'age' => 0, 'disease_label' => false );
	}

	/**
	 * Find every price in one string. $st carries context between strings of the same page.
	 * Each result: idx, offset, len, old, label, item, via ('name'|'page'|'').
	 */
	public static function scan_string( $str, &$st, $res, $page_ctx = null ) {
		$out = array();
		if ( ! is_string( $str ) || $str === '' ) {
			return $out;
		}
		preg_match_all( '/(?:^|>)([^<>]+)(?=<|$)/', $str, $m, PREG_OFFSET_CAPTURE );
		$pi = 0;
		foreach ( $m[1] as $pair ) {
			list( $raw, $off ) = $pair;
			$d = html_entity_decode( $raw, ENT_QUOTES | ENT_HTML5, 'UTF-8' );
			$d = trim( str_replace( "\xC2\xA0", ' ', preg_replace( '/\s+/', ' ', $d ) ) );
			if ( $d === '' ) {
				continue;
			}
			if ( preg_match_all( self::PRICE_RE, $raw, $pm, PREG_OFFSET_CAPTURE ) ) {
				foreach ( $pm[1] as $p ) {
					list( $digits, $poff ) = $p;
					$bind = null;
					$via  = '';
					if ( $st['ctx'] !== null ) {
						$bind      = $st['ctx'];
						$via       = 'name';
						$st['ctx'] = null;
					} elseif ( $page_ctx && preg_match( '/^(price|cost|from)\b/i', $d ) ) {
						$bind = $page_ctx;
						$via  = 'page';
					}
					$out[] = array(
						'idx'    => $pi,
						'offset' => $off + $poff,
						'len'    => strlen( $digits ),
						'old'    => $digits,
						'label'  => $bind ? $bind['label'] : null,
						'item'   => $bind ? $bind['item'] : null,
						'via'    => $via,
						'text'   => $d,
					);
					$pi++;
				}
				continue;
			}
			if ( self::is_noise( $d ) ) {
				continue;
			}
			$cand = null;
			if ( preg_match( '/^disease\s*:\s*(.*)$/i', $d, $mm ) ) {
				if ( trim( $mm[1] ) !== '' ) {
					$cand = trim( $mm[1] );
				} else {
					$st['disease_label'] = true;
					continue;
				}
			} elseif ( $st['disease_label'] ) {
				$cand = $d;
			} elseif ( preg_match( '/<tr[^>]*>\s*<td[^>]*>\s*$/i', substr( $str, 0, $off ) ) ) {
				$cand = $d;
			} elseif ( strlen( $d ) <= 60 && self::resolve( $res, $d ) ) {
				$cand = $d;
			}
			$st['disease_label'] = false;
			if ( $cand !== null ) {
				$st['ctx'] = array( 'label' => $cand, 'item' => self::resolve( $res, $cand ) );
				$st['age'] = 0;
			} else {
				$st['age']++;
				if ( $st['age'] > self::CTX_LIMIT ) {
					$st['ctx'] = null;
				}
			}
		}
		return $out;
	}

	/**
	 * Walk a string / nested array (Elementor data) in document order.
	 * $modify( $occ ) may return new digits to replace the price. Returns true if anything changed.
	 */
	public static function scan_data( &$data, $store, $res, $page_ctx, &$occs, $modify = null ) {
		$st      = self::new_state();
		$changed = false;
		self::walk( $data, '', $store, $st, $res, $page_ctx, $occs, $changed, $modify );
		return $changed;
	}

	private static function walk( &$node, $path, $store, &$st, $res, $page_ctx, &$occs, &$changed, $modify ) {
		if ( is_array( $node ) ) {
			foreach ( $node as $k => &$v ) {
				if ( is_string( $k ) && ( ( $k !== '' && $k[0] === '_' ) || in_array( $k, self::SKIP_KEYS, true ) ) ) {
					continue;
				}
				self::walk( $v, $path === '' ? (string) $k : $path . '/' . $k, $store, $st, $res, $page_ctx, $occs, $changed, $modify );
			}
			unset( $v );
			return;
		}
		if ( ! is_string( $node ) ) {
			return;
		}
		$found = self::scan_string( $node, $st, $res, $page_ctx );
		if ( ! $found ) {
			return;
		}
		$edits = array();
		foreach ( $found as $f ) {
			$f['id']   = $store . '|' . $path . '|' . $f['idx'];
			$f['path'] = $path;
			$occs[]    = $f;
			if ( $modify ) {
				$new = call_user_func( $modify, $f );
				if ( $new !== null && (string) $new !== (string) $f['old'] ) {
					$edits[] = array( $f['offset'], $f['len'], (string) $new );
				}
			}
		}
		if ( $edits ) {
			usort( $edits, function ( $a, $b ) {
				return $b[0] - $a[0];
			} );
			foreach ( $edits as $e ) {
				$node = substr_replace( $node, $e[2], $e[0], $e[1] );
			}
			$changed = true;
		}
	}
}
