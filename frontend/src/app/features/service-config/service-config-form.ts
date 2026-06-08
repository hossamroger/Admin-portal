import {
  ChangeDetectionStrategy, Component, computed, inject, signal, OnInit
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import {
  ServiceConfigPayload, ProcessInfoDto, StepDto, StepLinkDto,
  FeeDto, RequiredDocDto, RelatedDeptDto, TargetAudienceDto,
  ConfirmationScreenConfigDto, TargetAudienceLookup,
  ScreenInfoLookup, ComponentInfoLookup,
} from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog';

const FLAG_OPTS = ['Y', 'N'];

function emptyProcess(): ProcessInfoDto {
  return {
    processCode: '', processStatus: 'ACTIVE',
    processDisplayLabelAr: null, processDisplayLabelEn: null,
    processType: null, processOrder: null,
    bpmnProcessName: null, processInitiateSecurity: null,
    sendMail: 'N', sendSms: 'N', showOnInbox: 'N',
    inboxCustomColumn1: null, inboxCustomColumn1Ar: null,
    inboxCustomColumn2: null, inboxCustomColumn2Ar: null,
    inboxCustomColumn3: null, inboxCustomColumn3Ar: null,
    inboxCustomColumn4: null, inboxCustomColumn4Ar: null,
    inboxCustomColumn5: null, inboxCustomColumn5Ar: null,
    processDescAr: null, processDescEn: null,
    processTimeAr: null, processTimeEn: null,
    bpmProServiceCatalog: null, serviceIcon: null, serviceId: null,
    serviceUnicodeIcon: null, directAccess: null,
    allowTerms: null, allowRating: null,
    cardRedirectionRequired: null, walletRequireOtp: null,
    askBeforePayWallet: null, testFlag: 'N',
    surveyEntityId: null, surveyServiceId: null,
    actionType: null, actionUrl: null, preAuth: null,
    requireOtp: null, iosShow: 'Y', androidShow: 'Y', webShow: 'Y',
    testUserShow: null, releaseUserShow: null,
    activityProcessActionType: null, activityProcessActionUrl: null,
    internalActivityActionType: null, internalActivityActionUrl: null,
    groupCode: null, actionUrl2: null, adminPayment: null,
    iosActivityProcessActionType: null, iosInternalActivityActionType: null,
    androidActivityProcessActionType: null, androidInternalActivityActionType: null,
    iosActionType: null, androidActionType: null,
    platformCode: null, platformIcpUserId: null, isServiceCatalogOneTime: null,
  };
}

function emptyStep(): StepDto {
  return {
    requiredStepId: '', requiredStepDescAr: '', requiredStepDescEn: '',
    orderC: null, stepType: null, bpmnProcessName: null,
    linkDescAr: null, linkDescEn: null, attentionMsgAr: null, attentionMsgEn: null,
    stepSla: null, showLinkFlag: 'N', entityCode: null,
    statusLinks: [],
  };
}

function emptyLink(): StepLinkDto {
  return {
    requiredStatusCode: null, webLinkUrl: null, mobLinkFlag: 'N',
    bpmnProcessName: null, actionCode: null, iconUrl: null,
    labelAr: null, labelEn: null, iosActionCode: null,
    androidActionCode: null, platformCode: null,
  };
}

function emptyFee(): FeeDto {
  return { feesDescAr: '', feesDescEn: null, feesAmount: null, currencyAr: null, currencyEn: null, orderC: null };
}

function emptyDoc(): RequiredDocDto {
  return { requiredDocsDescAr: '', requiredDocsDescEn: '', orderC: null };
}

function emptyDept(): RelatedDeptDto {
  return { relatedDepartmentsDescAr: '', relatedDepartmentsDescEn: '', orderC: null, entityCode: null, entityCodeSrc: null };
}

function emptyAudience(): TargetAudienceDto {
  return { targetAudienceDescAr: '', targetAudienceDescEn: '', orderC: null, mainTargetAudienceSeq: null };
}

function emptyConfirmation(): ConfirmationScreenConfigDto {
  return {
    dsConfirmationScreenInfoId: null, requiredStepId: null,
    requiredStatusCode: null, hasPayment: 'N', hasRtRecord: 'N',
    serviceCode: null, showRefNo: 'Y', guest: 'N', action: null,
    components: [],
  };
}

@Component({
  selector: 'app-service-config-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, MatButtonModule, MatIconModule, MatTabsModule,
    MatTooltipModule, MatProgressBarModule, MatDialogModule,
    MatSelectModule, MatCheckboxModule,
  ],
  templateUrl: './service-config-form.html',
  styleUrl:    './service-config-form.scss',
})
export class ServiceConfigFormComponent implements OnInit {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  readonly loading  = signal(false);
  readonly saving   = signal(false);
  readonly isNew    = signal(false);

  // form data
  readonly info   = signal<ProcessInfoDto>(emptyProcess());
  readonly steps  = signal<StepDto[]>([]);
  readonly fees   = signal<FeeDto[]>([]);
  readonly docs   = signal<RequiredDocDto[]>([]);
  readonly depts  = signal<RelatedDeptDto[]>([]);
  readonly audiences = signal<TargetAudienceDto[]>([]);
  readonly confirmations = signal<ConfirmationScreenConfigDto[]>([]);

  // lookups
  readonly audienceLookup  = signal<TargetAudienceLookup[]>([]);
  readonly screenLookup    = signal<ScreenInfoLookup[]>([]);
  readonly componentLookup = signal<ComponentInfoLookup[]>([]);
  readonly statuses        = signal<string[]>([]);
  readonly types           = signal<string[]>([]);

  readonly title = computed(() => this.isNew() ? 'New Service' : this.info().processCode);

  readonly flagOpts = FLAG_OPTS;

  // true when the PK was auto-filled from MAX+1 (user must not edit it)
  readonly pkAutoFilled = signal(false);

  // which step index is expanded in the steps tab
  readonly expandedStep = signal<number | null>(null);

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('code');
    this.isNew.set(code === 'new');
    this.loadLookups();
    if (this.isNew()) {
      this.prefillPk();
    } else {
      this.loadService(code!);
    }
  }

  private prefillPk(): void {
    this.api.pkNextInfo('BPM_PROCESSES_INFO').subscribe({
      next: info => {
        const pkInfo = info['PROCESS_CODE'];
        if (pkInfo && !pkInfo.autoGenerated && pkInfo.nextValue != null) {
          this.info.update(v => ({ ...v, processCode: String(pkInfo.nextValue) }));
          this.pkAutoFilled.set(true);
        }
      },
      error: () => {},
    });
  }

  private loadLookups(): void {
    this.api.lookupTargetAudience().subscribe({ next: v => this.audienceLookup.set(v), error: () => {} });
    this.api.lookupScreenInfo().subscribe({ next: v => this.screenLookup.set(v), error: () => {} });
    this.api.lookupComponents().subscribe({ next: v => this.componentLookup.set(v), error: () => {} });
    this.api.lookupServiceStatuses().subscribe({ next: v => this.statuses.set(v), error: () => {} });
    this.api.lookupServiceTypes().subscribe({ next: v => this.types.set(v), error: () => {} });
  }

  private loadService(code: string): void {
    this.loading.set(true);
    this.api.getService(code).subscribe({
      next: p => {
        this.info.set(p.processInfo);
        this.steps.set(p.steps ?? []);
        this.fees.set(p.fees ?? []);
        this.docs.set(p.requiredDocs ?? []);
        this.depts.set(p.relatedDepts ?? []);
        this.audiences.set(p.targetAudiences ?? []);
        this.confirmations.set(p.confirmationScreens ?? []);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.notify.error(err, 'Failed to load service');
      },
    });
  }

  // ── Basic Info ────────────────────────────────────────────────────────────

  saveInfo(): void {
    this.saving.set(true);
    const payload = this.buildPayload();
    const call = this.isNew()
      ? this.api.createService(payload)
      : this.api.updateService(this.info().processCode, payload);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.notify.success(this.isNew() ? 'Service created' : 'Service updated');
        if (this.isNew()) this.router.navigate(['/service-config', this.info().processCode]);
      },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  private buildPayload(): ServiceConfigPayload {
    return {
      processInfo: this.info(),
      steps: this.steps(),
      fees: this.fees(),
      requiredDocs: this.docs(),
      relatedDepts: this.depts(),
      targetAudiences: this.audiences(),
      confirmationScreens: this.confirmations(),
    };
  }

  // ── Steps ─────────────────────────────────────────────────────────────────

  addStep(): void {
    this.steps.update(arr => [...arr, emptyStep()]);
    this.expandedStep.set(this.steps().length - 1);
  }
  removeStep(i: number): void {
    this.steps.update(arr => arr.filter((_, idx) => idx !== i));
    this.expandedStep.set(null);
  }
  toggleStep(i: number): void {
    this.expandedStep.update(v => v === i ? null : i);
  }

  addLink(step: StepDto): void {
    step.statusLinks = [...step.statusLinks, emptyLink()];
    this.steps.update(arr => [...arr]);
  }
  removeLink(step: StepDto, li: number): void {
    step.statusLinks = step.statusLinks.filter((_, idx) => idx !== li);
    this.steps.update(arr => [...arr]);
  }

  saveSteps(): void {
    this.saving.set(true);
    this.api.saveServiceSteps(this.info().processCode, this.steps()).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Steps saved'); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  // ── Fees ──────────────────────────────────────────────────────────────────

  addFee(): void { this.fees.update(arr => [...arr, emptyFee()]); }
  removeFee(i: number): void { this.fees.update(arr => arr.filter((_, idx) => idx !== i)); }
  saveFees(): void {
    this.saving.set(true);
    this.api.saveServiceFees(this.info().processCode, this.fees()).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Fees saved'); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  // ── Docs ──────────────────────────────────────────────────────────────────

  addDoc(): void { this.docs.update(arr => [...arr, emptyDoc()]); }
  removeDoc(i: number): void { this.docs.update(arr => arr.filter((_, idx) => idx !== i)); }
  saveDocs(): void {
    this.saving.set(true);
    this.api.saveServiceDocs(this.info().processCode, this.docs()).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Documents saved'); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  // ── Depts ─────────────────────────────────────────────────────────────────

  addDept(): void { this.depts.update(arr => [...arr, emptyDept()]); }
  removeDept(i: number): void { this.depts.update(arr => arr.filter((_, idx) => idx !== i)); }
  saveDepts(): void {
    this.saving.set(true);
    this.api.saveServiceDepts(this.info().processCode, this.depts()).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Providers saved'); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  // ── Audience ──────────────────────────────────────────────────────────────

  addAudience(): void { this.audiences.update(arr => [...arr, emptyAudience()]); }
  removeAudience(i: number): void { this.audiences.update(arr => arr.filter((_, idx) => idx !== i)); }
  saveAudiences(): void {
    this.saving.set(true);
    this.api.saveServiceAudience(this.info().processCode, this.audiences()).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Target audience saved'); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  // ── Confirmation ──────────────────────────────────────────────────────────

  addConfirmation(): void { this.confirmations.update(arr => [...arr, emptyConfirmation()]); }
  removeConfirmation(i: number): void { this.confirmations.update(arr => arr.filter((_, idx) => idx !== i)); }

  toggleComponent(cfg: ConfirmationScreenConfigDto, id: number): void {
    const exists = cfg.components.some(c => c.dsConfirmationComponentInfoId === id);
    cfg.components = exists
      ? cfg.components.filter(c => c.dsConfirmationComponentInfoId !== id)
      : [...cfg.components, { dsConfirmationComponentInfoId: id }];
    this.confirmations.update(arr => [...arr]);
  }

  hasComponent(cfg: ConfirmationScreenConfigDto, id: number): boolean {
    return cfg.components.some(c => c.dsConfirmationComponentInfoId === id);
  }

  saveConfirmations(): void {
    this.saving.set(true);
    this.api.saveServiceConfirmation(this.info().processCode, this.confirmations()).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Confirmation screens saved'); },
      error: err => { this.saving.set(false); this.notify.error(err, 'Save failed'); },
    });
  }

  // ── Delete service ────────────────────────────────────────────────────────

  deleteService(): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete service "${this.info().processCode}"? This will also delete all steps, fees, documents and related data.` },
      width: '480px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.api.deleteService(this.info().processCode).subscribe({
        next: () => { this.notify.success('Service deleted'); this.router.navigate(['/service-config']); },
        error: err => this.notify.error(err, 'Delete failed'),
      });
    });
  }

  back(): void { this.router.navigate(['/service-config']); }

  // helper to update info signal fields from inputs
  setInfo(field: keyof ProcessInfoDto, value: any): void {
    this.info.update(v => ({ ...v, [field]: value || null }));
  }
  setInfoVal(field: keyof ProcessInfoDto, value: any): void {
    this.info.update(v => ({ ...v, [field]: value }));
  }
}
