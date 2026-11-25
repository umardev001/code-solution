<?php
if ( ! defined( 'WP_UNINSTALL_PLUGIN' ) ) {
	exit;
}
$s = get_option( 'fvps_settings', array() );
if ( ! empty( $s['delete_data'] ) ) {
	global $wpdb;
	$wpdb->query( "DROP TABLE IF EXISTS {$wpdb->prefix}fvps_log" );
	delete_option( 'fvps_settings' );
	delete_option( 'fvps_last_scan' );
	delete_option( 'fvps_db_version' );
	delete_transient( 'fvps_source_cache' );
}
wp_clear_scheduled_hook( 'fvps_cron' );
