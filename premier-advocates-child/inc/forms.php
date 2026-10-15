<?php
/**
 * Contact form handler (no plugin required). Honeypot + time check + rate limit, sends via wp_mail().
 */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

add_action( 'wp_ajax_nopriv_pa_contact', 'pa_handle_contact' );
add_action( 'wp_ajax_pa_contact', 'pa_handle_contact' );

function pa_contact_reply( $ok, $message ) {
	if ( empty( $_POST['pa_js'] ) ) { // phpcs:ignore WordPress.Security.NonceVerification  (no-JS fallback)
		$back = wp_get_referer() ? wp_get_referer() : home_url( '/' );
		wp_safe_redirect( $ok ? add_query_arg( 'sent', '1', remove_query_arg( 'sent', $back ) ) : $back );
		exit;
	}
	$ok ? wp_send_json_success( array( 'message' => $message ) ) : wp_send_json_error( array( 'message' => $message ) );
}

function pa_handle_contact() {
	// phpcs:disable WordPress.Security.NonceVerification -- public form, protected by honeypot / rate limit.
	$ok_msg = 'Thank you – your message has been sent. We aim to respond within 12 working hours.';

	if ( ! empty( $_POST['website'] ) ) { // honeypot
		pa_contact_reply( true, $ok_msg );
	}
	$ts = isset( $_POST['pa_ts'] ) ? (int) $_POST['pa_ts'] : 0;
	if ( $ts && ( time() - $ts ) < 3 ) {
		pa_contact_reply( true, $ok_msg );
	}

	$ip  = isset( $_SERVER['REMOTE_ADDR'] ) ? sanitize_text_field( wp_unslash( $_SERVER['REMOTE_ADDR'] ) ) : '';
	$key = 'pa_rl_' . md5( $ip );
	$hit = (int) get_transient( $key );
	if ( $hit >= 5 ) {
		pa_contact_reply( false, 'Too many messages sent. Please try again later or email us directly.' );
	}

	$name    = isset( $_POST['name'] ) ? sanitize_text_field( wp_unslash( $_POST['name'] ) ) : '';
	$email   = isset( $_POST['email'] ) ? sanitize_email( wp_unslash( $_POST['email'] ) ) : '';
	$topic   = isset( $_POST['topic'] ) ? sanitize_text_field( wp_unslash( $_POST['topic'] ) ) : '';
	$message = isset( $_POST['message'] ) ? sanitize_textarea_field( wp_unslash( $_POST['message'] ) ) : '';
	$consent = ! empty( $_POST['consent'] );
	// phpcs:enable

	if ( ! $name || ! is_email( $email ) || ! $topic || ! $message || ! $consent ) {
		pa_contact_reply( false, 'Please complete all required fields with a valid email address.' );
	}

	$to      = get_option( 'pa_contact_to', get_option( 'admin_email' ) );
	$subject = sprintf( '[%s] %s – %s', wp_specialchars_decode( get_bloginfo( 'name' ), ENT_QUOTES ), $topic, $name );
	$body    = "Name: $name\nEmail: $email\nTopic: $topic\n\n$message\n";
	$headers = array( 'Reply-To: ' . $name . ' <' . $email . '>' );

	if ( ! wp_mail( $to, $subject, $body, $headers ) ) {
		pa_contact_reply( false, 'Sorry, your message could not be sent. Please email us directly at ' . $to . '.' );
	}
	set_transient( $key, $hit + 1, 10 * MINUTE_IN_SECONDS );
	pa_contact_reply( true, $ok_msg );
}
