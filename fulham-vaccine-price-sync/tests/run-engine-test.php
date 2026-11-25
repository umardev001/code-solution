<?php
// Usage: php run-engine-test.php <source.html> <page1.html> [page2.html ...]
define( 'FVPS_TESTING', 1 );
require __DIR__ . '/../includes/class-fvps-engine.php';
$src   = FVPS_Engine::parse_source( file_get_contents( $argv[1] ) );
echo 'Source items: ' . count( $src ) . "\n";
$res   = FVPS_Engine::build_resolver( $src );
foreach ( array_slice( $argv, 2 ) as $f ) {
	$html = file_get_contents( $f );
	// keep only the page body (drop header/footer menus)
	if ( preg_match( '#<div data-elementor-type="wp-page".*?(?=<footer|</body)#s', $html, $m ) ) {
		$html = $m[0];
	}
	$title = basename( $f, '.html' );
	$page  = FVPS_Engine::page_context( ucwords( str_replace( array( 'v_', '-' ), array( '', ' ' ), $title ) ), str_replace( 'v_', '', $title ), $res );
	$st    = FVPS_Engine::new_state();
	$occ   = FVPS_Engine::scan_string( $html, $st, $res, $page );
	echo "\n== $title (" . count( $occ ) . " prices)\n";
	foreach ( $occ as $o ) {
		if ( ! $o['label'] ) { continue; }
		printf( "  %-42s £%-5s -> %-8s via %-4s [%s]\n", $o['label'], $o['old'], $o['item'] ? '£' . $o['item']['price'] : 'UNMAPPED', $o['via'], $o['item'] ? $o['item']['name'] : '' );
	}
}
// round-trip replace check
$html = '<ul><li><strong>Disease:</strong> Hepatitis A</li><li>Price: £70/dose</li></ul>';
$st = FVPS_Engine::new_state(); $occs = array();
FVPS_Engine::scan_data( $html, 'content', $res, null, $occs, function ( $o ) { return $o['label'] ? '45' : null; } );
echo "\nReplace test: $html\n";
