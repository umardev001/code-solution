document.querySelectorAll('.burger').forEach(b=>b.onclick=()=>document.querySelector('.menu').classList.toggle('open'));
const cal=document.getElementById('cal');
if(cal){const first=new Date(2026,10,1).getDay();const off=(first+6)%7;
['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].forEach(d=>cal.insertAdjacentHTML('beforeend',`<div class="h">${d}</div>`));
for(let i=0;i<off;i++)cal.insertAdjacentHTML('beforeend','<div></div>');
for(let d=1;d<=30;d++){const dow=(off+d-1)%7;const av=dow<5&&d>=4;
cal.insertAdjacentHTML('beforeend',`<div class="d${av?' a':''}">${d}</div>`);}
cal.addEventListener('click',e=>{if(e.target.classList.contains('a')){cal.querySelectorAll('.sel').forEach(x=>x.classList.remove('sel'));e.target.classList.add('sel')}});}
const sl=document.getElementById('slides');
document.querySelectorAll('[data-dir]').forEach(b=>b.onclick=()=>sl&&sl.scrollBy({left:b.dataset.dir*280,behavior:'smooth'}));
