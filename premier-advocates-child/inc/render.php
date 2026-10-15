<?php
/**
 * Header / footer rendering.
 *
 * The header and footer are Elementor templates built with ElementsKit widgets (post type
 * "elementskit_template") and are loaded here. If Elementor/ElementsKit is not active (or the
 * template is missing) an identical static version is printed from data/header.json|footer.json,
 * so the site always has a working header, menu and footer.
 */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

function pa_render_part( $type ) {
	$html = '';
	$id   = (int) get_option( 'pa_tpl_' . $type );

	if ( $id && 'publish' === get_post_status( $id ) && class_exists( '\Elementor\Plugin' ) ) {
		$html = \Elementor\Plugin::$instance->frontend->get_builder_content_for_display( $id, true );
		// The header must contain a working menu, otherwise use the static version.
		if ( 'header' === $type && false === strpos( $html, 'elementskit-navbar-nav' ) ) {
			$html = '';
		}
	}

	if ( '' === trim( (string) $html ) ) {
		$html = pa_render_fallback( $type );
	}

	echo str_replace( '{{year}}', esc_html( gmdate( 'Y' ) ), $html ); // phpcs:ignore WordPress.Security.EscapeOutput
}

/** Static render of data/{header,footer}.json using the same DOM/classes as Elementor. */
function pa_render_fallback( $type ) {
	$file = PA_DIR . '/data/' . $type . '.json';
	if ( ! file_exists( $file ) ) {
		return '';
	}
	$data = json_decode( file_get_contents( $file ), true ); // phpcs:ignore WordPress.WP.AlternativeFunctions
	$data = pa_resolve_placeholders( $data, pa_url_map(), false );
	$out  = '';
	foreach ( (array) $data as $el ) {
		$out .= pa_fb_el( $el );
	}
	return $out;
}

function pa_fb_el( $e ) {
	$s = $e['settings'];
	if ( 'container' === $e['elType'] ) {
		$kids = '';
		foreach ( $e['elements'] as $c ) {
			$kids .= pa_fb_el( $c );
		}
		return sprintf( '<div class="elementor-element e-con e-flex e-con-full %s">%s</div>', esc_attr( $s['css_classes'] ?? '' ), $kids );
	}
	$w     = $e['widgetType'];
	$cls   = esc_attr( $s['_css_classes'] ?? '' );
	$inner = '';
	switch ( $w ) {
		case 'heading':
			$tag   = in_array( $s['header_size'], array( 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'div', 'span', 'p' ), true ) ? $s['header_size'] : 'div';
			$title = wp_kses_post( $s['title'] );
			if ( ! empty( $s['link']['url'] ) ) {
				$title = '<a href="' . esc_url( $s['link']['url'] ) . '">' . $title . '</a>';
			}
			$inner = "<$tag class=\"elementor-heading-title\">$title</$tag>";
			break;
		case 'text-editor':
			$inner = wp_kses_post( $s['editor'] );
			break;
		case 'button':
			$inner = sprintf( '<div class="elementor-button-wrapper"><a class="elementor-button elementor-button-link" href="%s"><span class="elementor-button-content-wrapper"><span class="elementor-button-text">%s</span></span></a></div>', esc_url( $s['link']['url'] ), esc_html( $s['text'] ) );
			break;
		case 'image':
			$img   = sprintf( '<img src="%s" alt="%s" width="58" height="58">', esc_url( $s['image']['url'] ), esc_attr( $s['image']['alt'] ?? '' ) );
			$inner = ! empty( $s['link']['url'] ) ? '<a href="' . esc_url( $s['link']['url'] ) . '">' . $img . '</a>' : $img;
			break;
		case 'icon-list':
			$items = '';
			foreach ( $s['icon_list'] as $it ) {
				$t = '<span class="elementor-icon-list-text">' . wp_kses_post( $it['text'] ) . '</span>';
				if ( ! empty( $it['link']['url'] ) ) {
					$t = '<a href="' . esc_url( $it['link']['url'] ) . '">' . $t . '</a>';
				}
				$items .= '<li class="elementor-icon-list-item elementor-repeater-item-' . esc_attr( $it['_id'] ) . '">' . $t . '</li>';
			}
			$inner = '<ul class="elementor-icon-list-items">' . $items . '</ul>';
			break;
		case 'ekit-nav-menu':
			$menu  = wp_nav_menu( array(
				'theme_location' => 'menu-1',
				'container'      => false,
				'menu_class'     => 'elementskit-navbar-nav',
				'fallback_cb'    => false,
				'echo'           => false,
				'depth'          => 1,
				'link_before'    => '',
				'items_wrap'     => '<ul id="%1$s" class="%2$s">%3$s</ul>',
			) );
			$menu  = preg_replace( '/<a /', '<a class="ekit-menu-nav-link" ', (string) $menu );
			$inner = '<div class="ekit-wid-con pa-fb"><button class="elementskit-menu-hamburger" type="button" aria-expanded="false" aria-label="Menu"><span class="elementskit-menu-hamburger-icon"></span><span class="elementskit-menu-hamburger-icon"></span><span class="elementskit-menu-hamburger-icon"></span></button><div class="elementskit-menu-container">' . $menu . '</div></div>';
			$cls  .= ' pa-fb';
			break;
	}
	return sprintf( '<div class="elementor-element elementor-widget elementor-widget-%s %s"><div class="elementor-widget-container">%s</div></div>', esc_attr( $w ), $cls, $inner );
}
