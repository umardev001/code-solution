<?php
/**
 * Header – renders the ElementsKit "Premier Header" template (editable in Elementor).
 * Falls back to an identical static header if Elementor/ElementsKit are not active yet.
 */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}
?><!doctype html>
<html <?php language_attributes(); ?>>
<head>
<meta charset="<?php bloginfo( 'charset' ); ?>">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="profile" href="https://gmpg.org/xfn/11">
<?php wp_head(); ?>
</head>
<body <?php body_class(); ?>>
<?php wp_body_open(); ?>
<a class="skip-link screen-reader-text" href="#content"><?php esc_html_e( 'Skip to content', 'premier-advocates' ); ?></a>
<header id="masthead" class="pa-header">
	<?php pa_render_part( 'header' ); ?>
</header>
