const puppeteer = require('puppeteer');
const { spawn } = require('child_process');

(async () => {
  const serve = spawn('npx', ['serve', 'frontend/dist/frontend/browser', '--single', '--listen', '4300'], 
    { cwd: '/home/user/Admin-portal', detached: true, stdio: 'ignore' });
  serve.unref();
  await new Promise(r => setTimeout(r, 3000));

  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox','--disable-setuid-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 900 });

  const intercepted = [];
  await page.setRequestInterception(true);
  page.on('request', req => {
    const path = req.url().replace(/^https?:\/\/[^/]+/,'');
    intercepted.push(`${req.method()} ${path}`);
    if (path === '/api/login') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({username:'admin',role:'ADMIN',fullName:'Admin User'})});
    if (path === '/api/me') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({username:'admin',role:'ADMIN',fullName:'Admin User'})});
    if (path === '/api/service-config/SVC001' && req.method()==='GET') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({processCode:'SVC001',processNameAr:'خدمة الاختبار',processNameEn:'Test Service',serviceType:'G',channelType:'W',slaHours:24,sortOrder:1,webShow:'T',testUserShow:'T',releaseUserShow:'F',oneTime:'F',requireLogin:'T',enableDraft:'F',enableSearch:'F',enablePrint:'F',callbackUrl:null,steps:[{stepId:'STEP1',stepNameAr:'الخطوة الأولى',stepNameEn:'Step One',stepOrder:1,statusLinks:[],components:[]}],fees:[],requiredDocs:[],relatedDepts:[],targetAudience:[],confirmationScreens:[],paymentCallbacks:[{id:1,url:'https://example.com/callback',status:'T',paymentStepOrder:1,sendNotification:'T'}]})});
    if (path.startsWith('/api/service-config') && req.method()==='GET') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({services:[{processCode:'SVC001',processNameEn:'Test Service',serviceType:'G',channelType:'W',webShow:'T'}],total:1})});
    if (path.startsWith('/api/')) return req.respond({status:200, contentType:'application/json', body: JSON.stringify({})});
    req.continue();
  });

  await page.goto('http://localhost:4300/service-config/SVC001', { waitUntil: 'networkidle0', timeout: 15000 });
  await new Promise(r => setTimeout(r, 2000));
  
  console.log('INTERCEPTED URLS:');
  intercepted.forEach(u => console.log(' ', u));
  
  const title = await page.evaluate(() => document.title);
  const h1 = await page.evaluate(() => document.querySelector('h1, .scf-title')?.textContent?.trim() || 'NOT FOUND');
  const processCodeVal = await page.evaluate(() => document.querySelector('input[placeholder*="SVC"], .field-input')?.value || 'NOT FOUND');
  const bodySnippet = await page.evaluate(() => document.body.innerText.substring(0, 500));
  
  console.log('PAGE TITLE:', title);
  console.log('H1/TITLE:', h1);
  console.log('PROCESS CODE FIELD VALUE:', processCodeVal);
  console.log('BODY SNIPPET:', bodySnippet);
  
  // Now click Steps tab
  const tabs = await page.$$('[role="tab"]');
  for (const tab of tabs) {
    const t = await tab.evaluate(el => el.textContent || '');
    if (t.includes('Step')) { await tab.click(); break; }
  }
  await new Promise(r => setTimeout(r, 1000));
  const stepsContent = await page.evaluate(() => document.body.innerText);
  console.log('\nAFTER STEPS TAB:');
  console.log('Has Payment Callback:', stepsContent.includes('Payment Callback'));
  console.log('Has example.com:', stepsContent.includes('example.com'));
  console.log('Steps snippet:', stepsContent.substring(0, 800));
  
  await browser.close();
  try { process.kill(-serve.pid); } catch(_) {}
})();
