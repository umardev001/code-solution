<?php
/**
 * Premier Advocates – Hello Elementor child theme.
 *
 * Design: Elementor widgets + ElementsKit header/footer. All styling lives in style.css.
 */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

define( 'PA_VERSION', '1.0.0' );
define( 'PA_DIR', get_stylesheet_directory() );
define( 'PA_URI', get_stylesheet_directory_uri() );

require_once PA_DIR . '/inc/render.php';
require_once PA_DIR . '/inc/setup.php';
require_once PA_DIR . '/inc/installer.php';
require_once PA_DIR . '/inc/forms.php';

/* ---------------------------------------------------------------- assets */
add_action( 'wp_enqueue_scripts', function () {
	wp_enqueue_style( 'pa-style', get_stylesheet_uri(), array(), filemtime( PA_DIR . '/style.css' ) );
	wp_enqueue_script( 'pa-main', PA_URI . '/assets/js/main.js', array(), filemtime( PA_DIR . '/assets/js/main.js' ), array( 'in_footer' => true, 'strategy' => 'defer' ) );
	wp_localize_script( 'pa-main', 'PA', array( 'ajax' => admin_url( 'admin-ajax.php' ) ) );

	// Speed: drop block-editor front-end CSS (pages are built with Elementor).
	wp_dequeue_style( 'wp-block-library' );
	wp_dequeue_style( 'wp-block-library-theme' );
	wp_dequeue_style( 'classic-theme-styles' );
	wp_dequeue_style( 'global-styles' );
}, 100 );

// Hello Elementor's optional button/form stylesheet is not needed (design is in style.css).
add_filter( 'hello_elementor_enqueue_theme_style', '__return_false' );
add_filter( 'hello_elementor_page_title', '__return_false' );
// Fonts are self-hosted, so stop Elementor loading Roboto etc. from Google.
add_filter( 'elementor/frontend/print_google_fonts', '__return_false' );

// Preload the two critical fonts.
add_action( 'wp_head', function () {
	foreach ( array( 'source-sans-3-var-latin', 'source-serif-4-var-latin' ) as $f ) {
		printf( '<link rel="preload" href="%s" as="font" type="font/woff2" crossorigin>' . "\n", esc_url( PA_URI . '/assets/fonts/' . $f . '.woff2' ) );
	}
}, 1 );

// Remove emoji + embed bloat.
remove_action( 'wp_head', 'print_emoji_detection_script', 7 );
remove_action( 'wp_print_styles', 'print_emoji_styles' );
remove_action( 'wp_head', 'wp_oembed_add_discovery_links' );
remove_action( 'wp_head', 'wlwmanifest_link' );
remove_action( 'wp_head', 'rsd_link' );
remove_action( 'wp_head', 'wp_generator' );

add_filter( 'body_class', function ( $c ) {
	$c[] = 'pa-site';
	return $c;
} );

/* ------------------------------------------------------------------- SEO */
add_filter( 'pre_get_document_title', function ( $title ) {
	if ( is_singular() ) {
		$t = get_post_meta( get_the_ID(), '_pa_seo_title', true );
		if ( $t ) {
			return $t;
		}
	}
	return $title;
} );
add_action( 'wp_head', function () {
	if ( is_singular() && ! defined( 'WPSEO_VERSION' ) && ! defined( 'RANK_MATH_VERSION' ) ) {
		$d = get_post_meta( get_the_ID(), '_pa_seo_desc', true );
		if ( $d ) {
			echo '<meta name="description" content="' . esc_attr( $d ) . '">' . "\n";
		}
	}
}, 2 );

add_action( 'after_setup_theme', function () {
	add_theme_support( 'title-tag' );
	add_theme_support( 'post-thumbnails' );
	add_theme_support( 'custom-logo' );
} );
