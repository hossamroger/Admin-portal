const puppeteer = require('puppeteer');
const { spawn } = require('child_process');

const MOCK_SERVICE = {
  processCode: 'SVC001', processNameAr: 'خدمة الاختبار', processNameEn: 'Test Service',
  serviceType: 'G', channelType: 'W', slaHours: 24, sortOrder: 1, webShow: 'T',
  testUserShow: 'T', releaseUserShow: 'F', oneTime: 'F', requireLogin: 'T',
  enableDraft: 'F', enableSearch: 'F', enablePrint: 'F', callbackUrl: null,
  steps: [{ stepId: 'STEP1', stepNameAr: 'خطوة', stepNameEn: 'Step One', stepOrder: 1, statusLinks: [], components: [] }],
  fees: [], requiredDocs: [], relatedDepts: [], targetAudience: [], confirmationScreens: [],
  paymentCallbacks: [{ id: 1, url: 'https://example.com/callback', status: 'T', paymentStepOrder: 1, sendNotification: 'T' }]
};

(async () => {
  const serve = spawn('npx', ['serve', 'frontend/dist/frontend/browser', '--single', '--listen', '4301'], 
    { cwd: '/home/user/Admin-portal', detached: true, stdio: 'ignore' });
  serve.unref();
  await new Promise(r => setTimeout(r, 3000));

  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox','--disable-setuid-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 900 });

  // Listen for console messages from the page
  page.on('console', msg => {
    if (msg.type() === 'error') console.log('PAGE ERROR:', msg.text());
  });
  page.on('response', async res => {
    const url = res.url().replace(/^https?:\/\/[^/]+/,'');
    if (url === '/api/service-config/SVC001') {
      const body = await res.text().catch(() => 'FAILED TO READ');
      console.log(`RESPONSE for ${url}:`, body.substring(0, 200));
    }
  });

  await page.setRequestInterception(true);
  page.on('request', req => {
    const path = req.url().replace(/^https?:\/\/[^/]+/,'');
    const m = req.method();
    if (path === '/api/me') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({username:'admin',role:'ADMIN',fullName:'Admin User'})});
    if (path === '/api/service-config/SVC001' && m==='GET') return req.respond({status:200, contentType:'application/json', body: JSON.stringify(MOCK_SERVICE)});
    if (path.startsWith('/api/service-config') && m==='GET') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({services:[],total:0})});
    if (path.startsWith('/api/')) return req.respond({status:200, contentType:'application/json', body: JSON.stringify({})});
    req.continue();
  });

  await page.goto('http://localhost:4301/service-config/SVC001', { waitUntil: 'networkidle0', timeout: 20000 });
  await new Promise(r => setTimeout(r, 3000)); // extra wait for signals
  
  const processCodeVal = await page.evaluate(() => {
    const inputs = Array.from(document.querySelectorAll('input'));
    return inputs.map(i => `${i.placeholder || 'no-ph'}=${i.value || 'empty'}`).slice(0,10).join(' | ');
  });
  console.log('INPUT VALUES:', processCodeVal);
  
  const stepsTabText = await page.evaluate(() => {
    const tabs = Array.from(document.querySelectorAll('[role="tab"]'));
    return tabs.map(t => t.textContent?.trim()).join(' | ');
  });
  console.log('TABS:', stepsTabText);
  
  // Screenshot
  await page.screenshot({ path: '/home/user/Admin-portal/debug-shot.png', fullPage: false });
  
  await browser.close();
  try { process.kill(-serve.pid); } catch(_) {}
})();
