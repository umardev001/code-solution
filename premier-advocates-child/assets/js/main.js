(function () {
  'use strict';

  /* Fallback mobile navigation (only used when ElementsKit is not rendering the menu) */
  document.querySelectorAll('.pa-fb .elementskit-menu-hamburger').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var wrap = btn.closest('.pa-fb');
      var open = wrap.classList.toggle('is-open');
      btn.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
  });

  /* Contact form */
  var form = document.getElementById('contact-form');
  if (!form) return;

  var status = form.querySelector('.form-status');
  var ts = form.querySelector('[name="pa_ts"]');
  if (ts) ts.value = Math.floor(Date.now() / 1000);
  var js = document.createElement('input');
  js.type = 'hidden'; js.name = 'pa_js'; js.value = '1';
  form.appendChild(js);

  // Pre-select topic / message when arriving from "Book this option" buttons.
  var q = new URLSearchParams(window.location.search);
  if (q.get('topic') === 'booking') {
    var sel = form.querySelector('#topic');
    if (sel) sel.value = 'Booking a consultation';
  }
  if (q.get('option')) {
    var msg = form.querySelector('#message');
    if (msg && !msg.value) msg.value = 'I would like to book: ' + q.get('option') + '.\n\n';
  }
  if (q.get('sent') === '1') showStatus('Thank you – your message has been sent. We aim to respond within 12 working hours.', false);


  function showStatus(text, isError) {
    if (!status) return;
    status.textContent = text;
    status.classList.add('show');
    status.classList.toggle('error', !!isError);
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    if (!form.reportValidity()) return;
    var btn = form.querySelector('button[type="submit"]');
    if (btn) btn.disabled = true;
    fetch(PA.ajax, { method: 'POST', body: new FormData(form), credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (res) {
        if (res && res.success) {
          showStatus(res.data && res.data.message ? res.data.message : 'Thank you – your message has been sent.', false);
          form.reset();
          if (ts) ts.value = Math.floor(Date.now() / 1000);
        } else {
          showStatus(res && res.data && res.data.message ? res.data.message : 'Sorry, something went wrong. Please email us directly.', true);
        }
      })
      .catch(function () { showStatus('Sorry, something went wrong. Please email us directly.', true); })
      .then(function () { if (btn) btn.disabled = false; });
  });
})();
