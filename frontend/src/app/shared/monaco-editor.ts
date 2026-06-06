import {
  AfterViewInit, Component, ElementRef, NgZone, OnDestroy, ChangeDetectionStrategy,
  effect, input, model, output, viewChild,
} from '@angular/core';

declare const window: any;

const MONACO_VERSION = '0.52.2';
const MONACO_CDN = `https://cdn.jsdelivr.net/npm/monaco-editor@${MONACO_VERSION}/min/vs`;

let monacoLoader: Promise<any> | null = null;

function loadMonaco(): Promise<any> {
  if (monacoLoader) return monacoLoader;
  monacoLoader = new Promise((resolve, reject) => {
    if (window.monaco) return resolve(window.monaco);
    const onLoaderReady = () => {
      window.require.config({ paths: { vs: MONACO_CDN } });
      window.MonacoEnvironment = {
        getWorkerUrl: () => `data:text/javascript;charset=utf-8,${encodeURIComponent(`
          self.MonacoEnvironment = { baseUrl: '${MONACO_CDN}/' };
          importScripts('${MONACO_CDN}/base/worker/workerMain.js');`)}`,
      };
      window.require(['vs/editor/editor.main'], () => resolve(window.monaco));
    };
    const script = document.createElement('script');
    script.src = `${MONACO_CDN}/loader.js`;
    script.onload = onLoaderReady;
    script.onerror = reject;
    document.head.appendChild(script);
  });
  return monacoLoader;
}

@Component({
  selector: 'app-monaco-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #host class="editor-host"></div>`,
  styles: [`:host, .editor-host { display: block; width: 100%; height: 100%; }`],
})
export class MonacoEditorComponent implements AfterViewInit, OnDestroy {
  readonly value = model('');
  readonly language = input('sql');
  readonly readOnly = input(false);
  readonly runRequested = output<void>();

  private readonly hostRef = viewChild.required<ElementRef<HTMLElement>>('host');
  private editor: any;

  constructor(private zone: NgZone) {
    effect(() => {
      const v = this.value();
      if (this.editor && this.editor.getValue() !== v) this.editor.setValue(v);
    });
  }

  async ngAfterViewInit(): Promise<void> {
    const monaco = await loadMonaco();
    this.zone.runOutsideAngular(() => {
      this.editor = monaco.editor.create(this.hostRef().nativeElement, {
        value: this.value(),
        language: this.language(),
        theme: 'vs',
        readOnly: this.readOnly(),
        automaticLayout: true,
        minimap: { enabled: false },
        fontFamily: 'JetBrains Mono, monospace',
        fontSize: 13,
        scrollBeyondLastLine: false,
        tabSize: 2,
        lineNumbers: 'on',
      });

      if (!this.readOnly()) {
        this.editor.onDidChangeModelContent(() => {
          this.zone.run(() => this.value.set(this.editor.getValue()));
        });

        this.editor.addCommand(
          monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter,
          () => this.zone.run(() => this.runRequested.emit()),
        );
      }
    });
  }

  ngOnDestroy(): void {
    this.editor?.dispose();
  }
}
