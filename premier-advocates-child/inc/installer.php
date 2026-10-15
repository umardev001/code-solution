<?php
/**
 * Appearance → Premier Setup: one-click install/activate of Elementor + ElementsKit Lite,
 * re-import of pages, and the enquiry recipient address. A notice is shown until both
 * plugins are active.
 */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

function pa_required_plugins() {
	return array(
		'elementor'       => array( 'name' => 'Elementor', 'file' => 'elementor/elementor.php' ),
		'elementskit-lite' => array( 'name' => 'ElementsKit Lite', 'file' => 'elementskit-lite/elementskit-lite.php' ),
	);
}

function pa_plugin_state( $slug ) {
	$p = pa_required_plugins()[ $slug ];
	if ( ! function_exists( 'is_plugin_active' ) ) {
		require_once ABSPATH . 'wp-admin/includes/plugin.php';
	}
	if ( is_plugin_active( $p['file'] ) ) {
		return 'active';
	}
	return file_exists( WP_PLUGIN_DIR . '/' . $p['file'] ) ? 'installed' : 'missing';
}

add_action( 'admin_menu', function () {
	add_theme_page( 'Premier Setup', 'Premier Setup', 'manage_options', 'premier-setup', 'pa_setup_page' );
} );

add_action( 'admin_notices', function () {
	if ( ! current_user_can( 'install_plugins' ) ) {
		return;
	}
	$todo = array();
	foreach ( pa_required_plugins() as $slug => $p ) {
		if ( 'active' !== pa_plugin_state( $slug ) ) {
			$todo[] = $p['name'];
		}
	}
	if ( ! $todo || ( isset( $_GET['page'] ) && 'premier-setup' === $_GET['page'] ) ) { // phpcs:ignore WordPress.Security.NonceVerification
		return;
	}
	printf(
		'<div class="notice notice-warning"><p><strong>Premier Advocates theme:</strong> %s %s required to edit and display the pages. <a class="button button-primary" href="%s">Install &amp; activate now</a></p></div>',
		esc_html( implode( ' + ', $todo ) ),
		count( $todo ) > 1 ? 'are' : 'is',
		esc_url( admin_url( 'themes.php?page=premier-setup' ) )
	);
} );

add_action( 'wp_ajax_pa_install_plugin', function () {
	check_ajax_referer( 'pa_setup', 'nonce' );
	if ( ! current_user_can( 'install_plugins' ) ) {
		wp_send_json_error( array( 'message' => 'You are not allowed to install plugins.' ) );
	}
	$slug = isset( $_POST['slug'] ) ? sanitize_key( wp_unslash( $_POST['slug'] ) ) : '';
	$all  = pa_required_plugins();
	if ( ! isset( $all[ $slug ] ) ) {
		wp_send_json_error( array( 'message' => 'Unknown plugin.' ) );
	}
	$file = $all[ $slug ]['file'];

	if ( 'missing' === pa_plugin_state( $slug ) ) {
		require_once ABSPATH . 'wp-admin/includes/file.php';
		require_once ABSPATH . 'wp-admin/includes/misc.php';
		require_once ABSPATH . 'wp-admin/includes/plugin-install.php';
		require_once ABSPATH . 'wp-admin/includes/class-wp-upgrader.php';
		$api = plugins_api( 'plugin_information', array( 'slug' => $slug, 'fields' => array( 'sections' => false ) ) );
		if ( is_wp_error( $api ) ) {
			wp_send_json_error( array( 'message' => $api->get_error_message() ) );
		}
		$upgrader = new Plugin_Upgrader( new WP_Ajax_Upgrader_Skin() );
		$result   = $upgrader->install( $api->download_link );
		if ( is_wp_error( $result ) || ! $result ) {
			wp_send_json_error( array( 'message' => is_wp_error( $result ) ? $result->get_error_message() : 'Install failed. Install it from Plugins → Add New.' ) );
		}
	}
	if ( 'active' !== pa_plugin_state( $slug ) ) {
		$act = activate_plugin( $file );
		if ( is_wp_error( $act ) ) {
			wp_send_json_error( array( 'message' => $act->get_error_message() ) );
		}
	}
	// Skip the plugins' welcome-screen redirects.
	delete_transient( 'elementor_activation_redirect' );
	delete_transient( 'elementskit-lite_activation_redirect' );
	pa_run_setup( false );
	wp_send_json_success( array( 'message' => $all[ $slug ]['name'] . ' is installed and active.' ) );
} );

add_action( 'admin_post_pa_reset_content', function () {
	check_admin_referer( 'pa_setup' );
	if ( ! current_user_can( 'manage_options' ) ) {
		wp_die( 'Not allowed' );
	}
	pa_run_setup( true );
	wp_safe_redirect( admin_url( 'themes.php?page=premier-setup&done=reset' ) );
	exit;
} );

add_action( 'admin_post_pa_save_settings', function () {
	check_admin_referer( 'pa_setup' );
	if ( ! current_user_can( 'manage_options' ) ) {
		wp_die( 'Not allowed' );
	}
	$mail = isset( $_POST['pa_contact_to'] ) ? sanitize_email( wp_unslash( $_POST['pa_contact_to'] ) ) : '';
	if ( is_email( $mail ) ) {
		update_option( 'pa_contact_to', $mail );
	}
	wp_safe_redirect( admin_url( 'themes.php?page=premier-setup&done=saved' ) );
	exit;
} );

function pa_setup_page() {
	$nonce = wp_create_nonce( 'pa_setup' );
	?>
	<div class="wrap">
		<h1>Premier Advocates – Setup</h1>
		<?php if ( ! empty( $_GET['done'] ) ) : // phpcs:ignore WordPress.Security.NonceVerification ?>
			<div class="notice notice-success is-dismissible"><p>Done.</p></div>
		<?php endif; ?>

		<h2>1. Required plugins</h2>
		<table class="widefat striped" style="max-width:720px">
			<?php foreach ( pa_required_plugins() as $slug => $p ) : $st = pa_plugin_state( $slug ); ?>
				<tr>
					<td><strong><?php echo esc_html( $p['name'] ); ?></strong></td>
					<td class="pa-state-<?php echo esc_attr( $slug ); ?>"><?php echo esc_html( 'active' === $st ? 'Active' : ( 'installed' === $st ? 'Installed, not active' : 'Not installed' ) ); ?></td>
					<td style="text-align:right">
						<?php if ( 'active' !== $st ) : ?>
							<button type="button" class="button button-primary pa-install" data-slug="<?php echo esc_attr( $slug ); ?>">Install &amp; activate</button>
						<?php else : ?>
							<span class="dashicons dashicons-yes-alt" style="color:#00a32a"></span>
						<?php endif; ?>
					</td>
				</tr>
			<?php endforeach; ?>
		</table>
		<p id="pa-msg" style="font-weight:600"></p>

		<h2>2. Content</h2>
		<p>Pages (Home, About Us, How We Can Help, Senior Executive Support, Book a Consultation, Contact, Privacy Policy, Terms of Use, Cookies), the main menu and the ElementsKit header/footer are created automatically when the theme is activated. Home is set as the front page.</p>
		<form method="post" action="<?php echo esc_url( admin_url( 'admin-post.php' ) ); ?>" onsubmit="return confirm('This replaces the content of all theme pages, the header and footer with the original design. Continue?');">
			<input type="hidden" name="action" value="pa_reset_content">
			<?php wp_nonce_field( 'pa_setup' ); ?>
			<button class="button">Reset / re-import all content</button>
		</form>

		<h2>3. Contact form recipient</h2>
		<form method="post" action="<?php echo esc_url( admin_url( 'admin-post.php' ) ); ?>">
			<input type="hidden" name="action" value="pa_save_settings">
			<?php wp_nonce_field( 'pa_setup' ); ?>
			<input type="email" name="pa_contact_to" class="regular-text" value="<?php echo esc_attr( get_option( 'pa_contact_to', 'office@premieradvocates.co.uk' ) ); ?>">
			<button class="button button-primary">Save</button>
		</form>

		<h2>Edit the header / footer</h2>
		<p>Both are ElementsKit templates: <strong>ElementsKit → Header &amp; Footer</strong> (or Templates in the admin menu) → edit with Elementor. The mobile menu uses the ElementsKit Nav Menu widget, reading <em>Appearance → Menus → Primary Menu</em>.</p>
	</div>
	<script>
	(function(){
		var msg=document.getElementById('pa-msg');
		document.querySelectorAll('.pa-install').forEach(function(b){
			b.addEventListener('click',function(){
				b.disabled=true; b.textContent='Working… (can take a minute)';
				var fd=new FormData(); fd.append('action','pa_install_plugin'); fd.append('nonce','<?php echo esc_js( $nonce ); ?>'); fd.append('slug',b.dataset.slug);
				fetch(ajaxurl,{method:'POST',body:fd,credentials:'same-origin'}).then(function(r){return r.json();}).then(function(r){
					msg.textContent=(r.data&&r.data.message)||''; msg.style.color=r.success?'#00a32a':'#d63638';
					if(r.success){setTimeout(function(){location.reload();},900);} else {b.disabled=false;b.textContent='Try again';}
				}).catch(function(){msg.textContent='Request failed – install the plugin from Plugins → Add New.';b.disabled=false;b.textContent='Try again';});
			});
		});
	})();
	</script>
	<?php
}
