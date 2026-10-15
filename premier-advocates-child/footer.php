<?php
/**
 * Footer – renders the ElementsKit "Premier Footer" template (same footer on every page).
 */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}
?>
<footer id="colophon" class="pa-footer">
	<?php pa_render_part( 'footer' ); ?>
</footer>
<?php wp_footer(); ?>
</body>
</html>
