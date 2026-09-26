'use strict';
const printButton = document.getElementById('print-notice');
if (printButton) {
    printButton.hidden = false;
    printButton.addEventListener('click', () => window.print());
}
