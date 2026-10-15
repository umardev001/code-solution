<?php
/**
 * One-click content setup: pages (built with Elementor), menu, header/footer templates.
 * Runs automatically when the theme is activated (and again when Elementor / ElementsKit are
 * activated). Existing pages are never overwritten unless you press "Reset content" in
 * Appearance → Premier Setup.
 */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/** slug => [ title, SEO title, SEO description ] */
function pa_pages() {
	return array(
		'home'                    => array( 'Home', 'Practical Support at Work | Premier Advocates', 'Premier Advocates provides practical support and representation for employees and professionals facing workplace issues.' ),
		'about-us'                => array( 'About Us', 'About Us | Premier Advocates', 'Premier Advocates provides practical, confidential and independent employment support for non-union members and anyone facing workplace issues.' ),
		'how-we-can-help'         => array( 'How We Can Help', 'How We Can Help | Premier Advocates', 'Independent employee representation and practical support through disciplinary, grievance, capability, settlement and tribunal processes.' ),
		'senior-executive-support' => array( 'Senior Executive Support', 'Senior Executive Support | Premier Advocates', 'Specialist, strategic support for executives, directors and professionals facing complex workplace challenges.' ),
		'book-a-consultation'     => array( 'Book a Consultation', 'Book a Consultation | Premier Advocates', 'Book a phone or video consultation with Premier Advocates for clear advice, practical guidance and the confidence to take the next step.' ),
		'contact'                 => array( 'Contact', 'Contact | Premier Advocates', 'Get in touch with Premier Advocates for confidential, supportive and practical help with workplace issues.' ),
	);
}

function pa_legal_pages() {
	return array(
		'privacy-policy' => array( 'Privacy Policy', pa_legal_privacy() ),
		'terms-of-use'   => array( 'Terms of Use', pa_legal_terms() ),
		'cookies'        => array( 'Cookies', pa_legal_cookies() ),
	);
}

/* ------------------------------------------------------------ placeholders */
function pa_url_map() {
	$map = array( 'ajax_url' => admin_url( 'admin-ajax.php' ) );
	foreach ( array_merge( array_keys( pa_pages() ), array_keys( pa_legal_pages() ) ) as $slug ) {
		$id = pa_page_id( $slug );
		if ( 'home' === $slug ) {
			$map['url:home'] = home_url( '/' );
		} else {
			$map[ 'url:' . $slug ] = $id ? get_permalink( $id ) : home_url( '/' . $slug . '/' );
		}
	}
	return $map;
}

function pa_page_id( $slug ) {
	$q = get_posts( array( 'post_type' => 'page', 'post_status' => 'any', 'meta_key' => '_pa_slug', 'meta_value' => $slug, 'numberposts' => 1, 'fields' => 'ids' ) );
	return $q ? (int) $q[0] : 0;
}

/** Recursively replace {{url:*}}, {{ajax_url}}, {{menu:primary}} and (optionally) {{img:*}}. */
function pa_resolve_placeholders( $data, $map, $sideload = true ) {
	if ( is_array( $data ) ) {
		if ( isset( $data['url'] ) && is_string( $data['url'] ) && 0 === strpos( $data['url'], '{{img:' ) ) {
			$name = substr( $data['url'], 6, -2 );
			$res  = $sideload ? pa_sideload_image( $name ) : array( 'url' => PA_URI . '/assets/images/' . pa_img_file( $name ), 'id' => '' );
			return array_merge( $data, $res );
		}
		foreach ( $data as $k => $v ) {
			$data[ $k ] = pa_resolve_placeholders( $v, $map, $sideload );
		}
		return $data;
	}
	if ( is_string( $data ) && false !== strpos( $data, '{{' ) ) {
		$data = preg_replace_callback( '/\{\{([a-z_]+(?::[a-z0-9-]+)?)\}\}/', function ( $m ) use ( $map ) {
			if ( 'menu:primary' === $m[1] ) {
				return (string) get_option( 'pa_menu_slug', 'primary-menu' );
			}
			return isset( $map[ $m[1] ] ) ? $map[ $m[1] ] : $m[0];
		}, $data );
	}
	return $data;
}

function pa_img_file( $name ) {
	return false === strpos( $name, '.' ) ? $name . '.jpg' : $name;
}

/** Copy a theme image into the Media Library once and return url + attachment id. */
function pa_sideload_image( $name ) {
	$file = pa_img_file( $name );
	$src  = PA_DIR . '/assets/images/' . $file;
	$ex   = get_posts( array( 'post_type' => 'attachment', 'post_status' => 'inherit', 'meta_key' => '_pa_asset', 'meta_value' => $file, 'numberposts' => 1, 'fields' => 'ids' ) );
	if ( $ex ) {
		return array( 'url' => wp_get_attachment_url( $ex[0] ), 'id' => $ex[0] );
	}
	if ( ! file_exists( $src ) ) {
		return array( 'url' => '', 'id' => '' );
	}
	require_once ABSPATH . 'wp-admin/includes/file.php';
	require_once ABSPATH . 'wp-admin/includes/image.php';
	$bits = wp_upload_bits( 'pa-' . $file, null, file_get_contents( $src ) ); // phpcs:ignore WordPress.WP.AlternativeFunctions
	if ( ! empty( $bits['error'] ) ) {
		return array( 'url' => PA_URI . '/assets/images/' . $file, 'id' => '' );
	}
	$type = preg_match( '/\.svg$/', $file ) ? 'image/svg+xml' : 'image/jpeg';
	$id   = wp_insert_attachment( array(
		'post_mime_type' => $type,
		'post_title'     => sanitize_text_field( preg_replace( '/\.[a-z]+$/', '', $file ) ),
		'post_status'    => 'inherit',
	), $bits['file'] );
	if ( is_wp_error( $id ) || ! $id ) {
		return array( 'url' => $bits['url'], 'id' => '' );
	}
	if ( 'image/svg+xml' !== $type ) {
		wp_update_attachment_metadata( $id, wp_generate_attachment_metadata( $id, $bits['file'] ) );
	}
	update_post_meta( $id, '_pa_asset', $file );
	return array( 'url' => $bits['url'], 'id' => $id );
}

/* ------------------------------------------------------------------ setup */
function pa_save_elementor( $post_id, $json_file, $type = 'wp-page' ) {
	$raw = file_get_contents( PA_DIR . '/data/' . $json_file ); // phpcs:ignore WordPress.WP.AlternativeFunctions
	$els = pa_resolve_placeholders( json_decode( $raw, true ), pa_url_map() );
	update_post_meta( $post_id, '_elementor_data', wp_slash( wp_json_encode( $els ) ) );
	update_post_meta( $post_id, '_elementor_edit_mode', 'builder' );
	update_post_meta( $post_id, '_elementor_template_type', $type );
	update_post_meta( $post_id, '_elementor_version', defined( 'ELEMENTOR_VERSION' ) ? ELEMENTOR_VERSION : '3.25.0' );
	update_post_meta( $post_id, '_elementor_page_settings', array() );
	delete_post_meta( $post_id, '_elementor_css' );
}

function pa_run_setup( $force = false ) {
	if ( get_transient( 'pa_setup_running' ) ) {
		return;
	}
	set_transient( 'pa_setup_running', 1, 120 );

	// 1) create every page first so internal links can be resolved.
	foreach ( pa_pages() as $slug => $info ) {
		if ( ! pa_page_id( $slug ) ) {
			$existing = get_page_by_path( 'home' === $slug ? 'home' : $slug );
			$id       = $existing ? $existing->ID : wp_insert_post( array(
				'post_type'   => 'page',
				'post_status' => 'publish',
				'post_title'  => $info[0],
				'post_name'   => $slug,
			) );
			if ( $id && ! is_wp_error( $id ) ) {
				update_post_meta( $id, '_pa_slug', $slug );
				update_post_meta( $id, '_pa_fresh', 1 );
			}
		}
	}
	foreach ( pa_legal_pages() as $slug => $info ) {
		if ( ! pa_page_id( $slug ) ) {
			$existing = get_page_by_path( $slug );
			$id       = $existing ? $existing->ID : wp_insert_post( array(
				'post_type'    => 'page',
				'post_status'  => 'publish',
				'post_title'   => $info[0],
				'post_name'    => $slug,
				'post_content' => $info[1],
			) );
			if ( $id && ! is_wp_error( $id ) ) {
				update_post_meta( $id, '_pa_slug', $slug );
				update_post_meta( $id, '_wp_page_template', 'elementor_header_footer' );
			}
		}
	}

	// 2) menu (needed before the header template is resolved).
	pa_setup_menu( $force );

	// 3) Elementor content.
	foreach ( pa_pages() as $slug => $info ) {
		$id = pa_page_id( $slug );
		if ( ! $id ) {
			continue;
		}
		update_post_meta( $id, '_pa_seo_title', $info[1] );
		update_post_meta( $id, '_pa_seo_desc', $info[2] );
		update_post_meta( $id, '_wp_page_template', 'elementor_header_footer' );
		if ( $force || get_post_meta( $id, '_pa_fresh', true ) ) {
			pa_save_elementor( $id, $slug . '.json' );
			delete_post_meta( $id, '_pa_fresh' );
		}
	}

	// 4) header + footer (ElementsKit Header & Footer builder templates).
	foreach ( array( 'header' => 'Premier Header', 'footer' => 'Premier Footer' ) as $type => $title ) {
		$id = (int) get_option( 'pa_tpl_' . $type );
		if ( ! $id || ! get_post( $id ) ) {
			$id = wp_insert_post( array( 'post_type' => 'elementskit_template', 'post_status' => 'publish', 'post_title' => $title ) );
			update_option( 'pa_tpl_' . $type, $id );
			update_post_meta( $id, 'elementskit_template_type', $type );
			// The theme loads these templates itself (header.php / footer.php), so ElementsKit's own
			// display conditions stay off to avoid printing them twice.
			update_post_meta( $id, 'elementskit_template_activation', 'no' );
			$force_tpl = true;
		} else {
			$force_tpl = $force;
		}
		if ( $force_tpl ) {
			pa_save_elementor( $id, $type . '.json', 'wp-post' );
		}
	}

	// 5) site settings.
	$front = pa_page_id( 'home' );
	if ( $front && ( $force || ! get_option( 'pa_front_set' ) ) ) {
		update_option( 'show_on_front', 'page' );
		update_option( 'page_on_front', $front );
		update_option( 'pa_front_set', 1 );
	}
	if ( ! get_option( 'permalink_structure' ) ) {
		update_option( 'permalink_structure', '/%postname%/' );
	}
	$name = get_option( 'blogname' );
	if ( ! $name || 'My WordPress Blog' === $name || 'WordPress' === $name ) {
		update_option( 'blogname', 'Premier Advocates' );
		update_option( 'blogdescription', 'Stand With Confidence' );
	}
	update_option( 'elementor_disable_color_schemes', 'yes' );
	update_option( 'elementor_disable_typography_schemes', 'yes' );
	update_option( 'elementor_google_font', '0' );
	if ( false === get_option( 'pa_contact_to' ) ) {
		update_option( 'pa_contact_to', 'office@premieradvocates.co.uk' );
	}

	update_option( 'pa_setup_done', PA_VERSION );
	flush_rewrite_rules( false );
	if ( class_exists( '\Elementor\Plugin' ) && isset( \Elementor\Plugin::$instance->files_manager ) ) {
		\Elementor\Plugin::$instance->files_manager->clear_cache();
	}
	delete_transient( 'pa_setup_running' );
}

function pa_setup_menu( $force = false ) {
	$name = 'Primary Menu';
	$menu = wp_get_nav_menu_object( $name );
	if ( $menu && ! $force ) {
		update_option( 'pa_menu_slug', $menu->slug );
		pa_assign_menu( $menu->term_id );
		return;
	}
	if ( $menu ) {
		foreach ( (array) wp_get_nav_menu_items( $menu->term_id ) as $item ) {
			wp_delete_post( $item->ID, true );
		}
		$menu_id = $menu->term_id;
	} else {
		$menu_id = wp_create_nav_menu( $name );
		if ( is_wp_error( $menu_id ) ) {
			return;
		}
	}
	$items = array(
		array( 'Home', 'home' ),
		array( 'About Us', 'about-us' ),
		array( 'Problems at Work', '#problems' ),
		array( 'How We Can Help', 'how-we-can-help' ),
		array( 'Senior Executive Support', 'senior-executive-support' ),
		array( 'Book a Consultation', 'book-a-consultation' ),
		array( 'Contact', 'contact' ),
	);
	$pos = 1;
	foreach ( $items as $it ) {
		$args = array( 'menu-item-title' => $it[0], 'menu-item-status' => 'publish', 'menu-item-position' => $pos++ );
		if ( '#problems' === $it[1] ) {
			$args['menu-item-type'] = 'custom';
			$args['menu-item-url']  = home_url( '/#problems' );
		} else {
			$pid = pa_page_id( $it[1] );
			if ( ! $pid ) {
				continue;
			}
			$args['menu-item-type']      = 'post_type';
			$args['menu-item-object']    = 'page';
			$args['menu-item-object-id'] = $pid;
		}
		wp_update_nav_menu_item( $menu_id, 0, $args );
	}
	$obj = wp_get_nav_menu_object( $menu_id );
	update_option( 'pa_menu_slug', $obj ? $obj->slug : 'primary-menu' );
	pa_assign_menu( $menu_id );
}

function pa_assign_menu( $menu_id ) {
	$locs = get_theme_mod( 'nav_menu_locations', array() );
	if ( empty( $locs['menu-1'] ) ) {
		$locs['menu-1'] = $menu_id;
		set_theme_mod( 'nav_menu_locations', $locs );
	}
}

/* ----------------------------------------------------------------- hooks */
add_action( 'after_switch_theme', function () {
	pa_run_setup( false );
} );
// Theme activated through WP-CLI / deploy tools, or content missing for any reason.
add_action( 'admin_init', function () {
	if ( ! get_option( 'pa_setup_done' ) && current_user_can( 'manage_options' ) ) {
		pa_run_setup( false );
	}
} );
// Re-run (non destructive) when the page builders get activated so caches are cleared.
add_action( 'activated_plugin', function ( $plugin ) {
	if ( 0 === strpos( $plugin, 'elementor/' ) || 0 === strpos( $plugin, 'elementskit-lite/' ) ) {
		pa_run_setup( false );
	}
} );

/* ------------------------------------------------------------ legal text */
function pa_legal_note() {
	return '<p class="pa-note">This page contains standard starter wording. Please have it reviewed by a qualified adviser and edit it (Pages → this page) so that it reflects exactly how Premier Advocates handles data and operates its website.</p>';
}
function pa_legal_wrap( $title, $body ) {
	return '<div class="pa-legal"><h1>' . esc_html( $title ) . '</h1>' . pa_legal_note() . $body . '</div>';
}
function pa_legal_privacy() {
	return pa_legal_wrap( 'Privacy Policy', '
<h2>Who we are</h2><p>Premier Advocates provides employment support and representation across the United Kingdom. You can contact us at <a href="mailto:office@premieradvocates.co.uk">office@premieradvocates.co.uk</a>.</p>
<h2>What we collect</h2><p>When you use our contact form or book a consultation we collect the details you give us (such as your name, email address and the information you share about your situation). We use it only to respond to your enquiry and to provide our services.</p>
<h2>How we use and protect it</h2><p>Information is treated in confidence, stored securely and is not sold or shared for marketing. We may share it with service providers who help us deliver our services (for example payment or video-call providers) and only where necessary.</p>
<h2>Your rights</h2><p>Under UK data protection law you may ask to access, correct or delete the personal data we hold about you, or object to its use. Contact us using the email address above.</p>' );
}
function pa_legal_terms() {
	return pa_legal_wrap( 'Terms of Use', '
<h2>About this website</h2><p>The information on this website is general information and should not be treated as advice on your individual circumstances.</p>
<h2>Our services</h2><p>Services are provided on the basis described when you book a consultation. Nothing on this website creates a contract until a booking is confirmed.</p>
<h2>Use of the website</h2><p>You may view and print pages for your own personal use. You must not misuse the website or attempt to interfere with its operation.</p>
<h2>Contact</h2><p>Questions about these terms can be sent to <a href="mailto:office@premieradvocates.co.uk">office@premieradvocates.co.uk</a>.</p>' );
}
function pa_legal_cookies() {
	return pa_legal_wrap( 'Cookies', '
<h2>What are cookies?</h2><p>Cookies are small text files stored on your device by websites you visit. They help websites work properly and provide information to site owners.</p>
<h2>How we use them</h2><p>This website uses only the cookies needed for it to function. If analytics or marketing tools are added in future, this page and a consent banner should be updated.</p>
<h2>Managing cookies</h2><p>You can control or delete cookies through your browser settings.</p>' );
}
