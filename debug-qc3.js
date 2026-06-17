const puppeteer = require('puppeteer');
const { spawn } = require('child_process');

const MOCK_SERVICE = {
  processInfo: { processCode: 'SVC001', processNameAr: 'خدمة', processNameEn: 'Test Service', serviceType: 'G', channelType: 'W', slaHours: 24, sortOrder: 1, webShow: 'T', testUserShow: 'T', releaseUserShow: 'F', oneTime: 'F', requireLogin: 'T', enableDraft: 'F', enableSearch: 'F', enablePrint: 'F', callbackUrl: null },
  steps: [{ requiredStepId: 'STEP1', requiredStepDescAr: 'خطوة', requiredStepDescEn: 'Step One', orderC: 1, statusLinks: [] }],
  fees: [], requiredDocs: [], relatedDepts: [], targetAudiences: [], confirmationScreens: [],
  paymentCallbacks: [{ id: 1, url: 'https://example.com/callback', status: 'T', paymentStepOrder: 1, sendNotification: 'T' }]
};

(async () => {
  const serve = spawn('npx', ['serve', 'frontend/dist/frontend/browser', '--single', '--listen', '4302'], { cwd: '/home/user/Admin-portal', detached: true, stdio: 'ignore' });
  serve.unref();
  await new Promise(r => setTimeout(r, 3000));

  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox','--disable-setuid-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 1000 });
  
  page.on('console', msg => { if (msg.type() === 'error') console.log('PAGE ERROR:', msg.text()); });
  page.on('response', async res => {
    const path = res.url().replace(/^https?:\/\/[^/]+/,'');
    if (path === '/api/service-config/SVC001') {
      const body = await res.text().catch(() => 'FAILED');
      console.log('RESPONSE BODY:', body.substring(0, 300));
    }
  });

  await page.setRequestInterception(true);
  page.on('request', req => {
    const path = req.url().replace(/^https?:\/\/[^/]+/,'');
    const m = req.method();
    if (path === '/api/me') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({username:'admin',role:'ADMIN',fullName:'Admin'})});
    if (path === '/api/service-config/SVC001' && m === 'GET') return req.respond({status:200, contentType:'application/json', body: JSON.stringify(MOCK_SERVICE)});
    if (path.startsWith('/api/service-config') && m === 'GET') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({services:[],total:0})});
    if (path.startsWith('/api/')) return req.respond({status:200, contentType:'application/json', body: JSON.stringify({})});
    req.continue();
  });

  await page.goto('http://localhost:4302/service-config/SVC001', { waitUntil: 'networkidle0', timeout: 20000 });
  await new Promise(r => setTimeout(r, 3000));
  
  // Read component state via Angular internals
  const compState = await page.evaluate(() => {
    try {
      const el = document.querySelector('app-service-config-form');
      if (!el) return 'NO ELEMENT';
      const ng = window.ng || window['ng'];
      if (!ng) return 'NO NG';
      const comp = ng.getComponent(el);
      if (!comp) return 'NO COMP';
      return {
        processCode: comp.info()?.processCode,
        stepsLength: comp.steps()?.length,
        paymentCallbacksLength: comp.paymentCallbacks()?.length,
        isNew: comp.isNew()
      };
    } catch(e) {
      return 'ERROR: ' + e.message;
    }
  });
  console.log('COMPONENT STATE:', JSON.stringify(compState));
  
  const tabs = await page.evaluate(() =>
    Array.from(document.querySelectorAll('[role="tab"]')).map(t => t.textContent?.trim()).join(' | '));
  console.log('TABS:', tabs);
  
  await page.screenshot({ path: '/home/user/Admin-portal/debug-qc3.png', fullPage: false });
  
  await browser.close();
  try { process.kill(-serve.pid); } catch(_) {}
})();
