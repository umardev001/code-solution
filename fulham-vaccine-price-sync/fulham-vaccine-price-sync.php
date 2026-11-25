<?php
/**
 * Plugin Name: Fulham Vaccine Price Sync
 * Description: One-click sync of vaccine prices from the Fulham Travel Clinic price list to every page of this site, with full history and rollback.
 * Version:     1.0.0
 * Author:      Umar
 * License:     GPL-2.0-or-later
 * Text Domain: fulham-vaccine-price-sync
 */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

define( 'FVPS_VERSION', '1.0.0' );
define( 'FVPS_FILE', __FILE__ );

require_once __DIR__ . '/includes/class-fvps-engine.php';
require_once __DIR__ . '/includes/class-fvps-core.php';
require_once __DIR__ . '/includes/class-fvps-admin.php';

register_activation_hook( __FILE__, array( 'FVPS_Core', 'install' ) );
register_deactivation_hook( __FILE__, function () {
	wp_clear_scheduled_hook( 'fvps_cron' );
} );

add_action( 'plugins_loaded', function () {
	if ( get_option( 'fvps_db_version' ) !== FVPS_VERSION ) {
		FVPS_Core::install();
	}
	add_action( 'fvps_cron', array( 'FVPS_Core', 'cron_sync' ) );
	if ( is_admin() ) {
		FVPS_Admin::init();
	}
} );
