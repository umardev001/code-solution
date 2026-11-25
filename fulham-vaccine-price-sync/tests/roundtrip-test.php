<?php
// Simulates an Elementor page (JSON) and checks replace + rollback + stale guard.
define( 'FVPS_TESTING', 1 );
require __DIR__ . '/../includes/class-fvps-engine.php';
$src = FVPS_Engine::parse_source( file_get_contents( $argv[1] ) );
$res = FVPS_Engine::build_resolver( $src );

$table = '<table><tbody><tr><td>Hepatitis A</td><td>18 years +</td><td>TWO DOSES</td><td>0, 6 months</td><td>AVAXIM ®</td><td>£70/dose</td></tr>'
       . '<tr><td>Typhoid</td><td>2 years +</td><td>ONE</td><td>SINGLE</td><td>TYPHIM</td><td>&pound;55/dose</td></tr></tbody></table>';
$page = array( array( 'id' => 'a1', 'elType' => 'container', 'elements' => array(
	array( 'id' => 'w1', 'elType' => 'widget', 'widgetType' => 'text-editor', 'settings' => array( 'editor' => $table, '_css_classes' => 'Cholera £999' ) ),
	array( 'id' => 'w2', 'elType' => 'widget', 'widgetType' => 'heading', 'settings' => array( 'title' => 'Yellow Fever' ) ),
	array( 'id' => 'w3', 'elType' => 'widget', 'widgetType' => 'heading', 'settings' => array( 'title' => 'Price: £85 per dose' ) ),
) ) );
$json = json_encode( $page );
$data = json_decode( $json, true );

$occs = array();
$ch = FVPS_Engine::scan_data( $data, 'el', $res, null, $occs, function ( $o ) { return $o['item'] ? $o['item']['price'] : null; } );
echo "changed: " . var_export( $ch, true ) . "\n";
foreach ( $occs as $o ) { echo "  {$o['id']}  {$o['label']}  £{$o['old']}\n"; }
echo json_encode( $data, JSON_UNESCAPED_UNICODE ) . "\n";
assert( strpos( $data[0]['elements'][0]['settings']['editor'], '£45/dose' ) !== false );
assert( strpos( $data[0]['elements'][0]['settings']['editor'], '&pound;30/dose' ) !== false );
assert( $data[0]['elements'][0]['settings']['_css_classes'] === 'Cholera £999' ); // untouched
assert( $data[0]['elements'][2]['settings']['title'] === 'Price: £85 per dose' ); // yellow fever 85 = same

// rollback: restore old values only where current == new
$old = array( 'el|0/elements/0/settings/editor|0' => array( '45', '70' ), 'el|0/elements/0/settings/editor|1' => array( '30', '55' ) );
$o2 = array();
FVPS_Engine::scan_data( $data, 'el', $res, null, $o2, function ( $o ) use ( $old ) {
	return isset( $old[ $o['id'] ] ) && (float) $old[ $o['id'] ][0] === (float) $o['old'] ? $old[ $o['id'] ][1] : null;
} );
assert( strpos( $data[0]['elements'][0]['settings']['editor'], '£70/dose' ) !== false );
assert( strpos( $data[0]['elements'][0]['settings']['editor'], '&pound;55/dose' ) !== false );
echo "ROUNDTRIP OK\n";
