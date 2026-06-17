const puppeteer = require('puppeteer');
const { spawn } = require('child_process');

const MOCK_SERVICE = {
  processInfo: { processCode: 'SVC001', processNameAr: 'خدمة', processNameEn: 'Test Service', serviceType: 'G', channelType: 'W', slaHours: 24, sortOrder: 1, webShow: 'T', testUserShow: 'T', releaseUserShow: 'F', oneTime: 'F', requireLogin: 'T', enableDraft: 'F', enableSearch: 'F', enablePrint: 'F', callbackUrl: null },
  steps: [
    { requiredStepId: 'STEP1', requiredStepDescAr: 'خطوة', requiredStepDescEn: 'Payment Step', orderC: 2, statusLinks: [] },
    { requiredStepId: 'STEP2', requiredStepDescAr: 'خطوة2', requiredStepDescEn: 'Confirm Step', orderC: 3, statusLinks: [] }
  ],
  fees: [], requiredDocs: [], relatedDepts: [], targetAudiences: [], confirmationScreens: [],
  paymentCallbacks: [
    { id: 1, url: 'https://example.com/payment-confirm', status: 'T', paymentStepOrder: 2, sendNotification: 'T' },
    { id: 2, url: 'https://example.com/notify-only',    status: 'F', paymentStepOrder: 3, sendNotification: 'T' },
  ]
};

(async () => {
  const serve = spawn('npx', ['serve', 'frontend/dist/frontend/browser', '--single', '--listen', '4299'], { cwd: '/home/user/Admin-portal', detached: true, stdio: 'ignore' });
  serve.unref();
  await new Promise(r => setTimeout(r, 3000));

  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox','--disable-setuid-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 1200 });

  await page.setRequestInterception(true);
  page.on('request', req => {
    const path = req.url().replace(/^https?:\/\/[^/]+/,'');
    const m = req.method();
    if (path === '/api/me') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({username:'admin',role:'ADMIN',fullName:'Admin'})});
    if (path === '/api/service-config/SVC001' && m === 'GET') return req.respond({status:200, contentType:'application/json', body: JSON.stringify(MOCK_SERVICE)});
    if (path === '/api/service-config/lookups/audience')    return req.respond({status:200, contentType:'application/json', body:'[]'});
    if (path === '/api/service-config/lookups/screen-info') return req.respond({status:200, contentType:'application/json', body:'[]'});
    if (path === '/api/service-config/lookups/components')  return req.respond({status:200, contentType:'application/json', body:'[]'});
    if (path === '/api/service-config/lookups/statuses')    return req.respond({status:200, contentType:'application/json', body:'[]'});
    if (path === '/api/service-config/lookups/types')       return req.respond({status:200, contentType:'application/json', body:'[]'});
    if (path.startsWith('/api/service-config') && m === 'GET') return req.respond({status:200, contentType:'application/json', body: JSON.stringify({services:[],total:0})});
    if (path.startsWith('/api/')) return req.respond({status:200, contentType:'application/json', body: JSON.stringify({})});
    req.continue();
  });

  await page.goto('http://localhost:4299/service-config/SVC001', { waitUntil: 'networkidle0', timeout: 20000 });
  await new Promise(r => setTimeout(r, 2500));

  // Click Steps tab
  const tabs = await page.$$('[role="tab"]');
  for (const tab of tabs) {
    if ((await tab.evaluate(el => el.textContent || '')).includes('Step')) { await tab.click(); break; }
  }
  await new Promise(r => setTimeout(r, 1000));

  // Expand first step
  const stepHeaders = await page.$$('.step-header');
  await stepHeaders[0].click();
  await new Promise(r => setTimeout(r, 600));

  // Scroll tab body to bottom to show payment callbacks
  await page.evaluate(() => {
    const c = document.querySelector('.mat-mdc-tab-body-active .mat-mdc-tab-body-content');
    if (c) c.scrollTop = c.scrollHeight;
  });
  await new Promise(r => setTimeout(r, 300));
  await page.screenshot({ path: '/home/user/Admin-portal/visual-step1-callbacks.png' });
  console.log('Step 1 expanded, scrolled to callbacks');

  // Now collapse step 1, expand step 2
  await stepHeaders[0].click();
  await new Promise(r => setTimeout(r, 300));
  // scroll back to top
  await page.evaluate(() => {
    const c = document.querySelector('.mat-mdc-tab-body-active .mat-mdc-tab-body-content');
    if (c) c.scrollTop = 0;
  });
  await stepHeaders[1].click();
  await new Promise(r => setTimeout(r, 600));
  await page.evaluate(() => {
    const c = document.querySelector('.mat-mdc-tab-body-active .mat-mdc-tab-body-content');
    if (c) c.scrollTop = c.scrollHeight;
  });
  await new Promise(r => setTimeout(r, 300));
  await page.screenshot({ path: '/home/user/Admin-portal/visual-step2-callbacks.png' });
  console.log('Step 2 expanded, scrolled to callbacks');

  await browser.close();
  try { process.kill(-serve.pid); } catch(_) {}
})();
