import {
  ChangeDetectionStrategy, Component, computed, effect, ElementRef,
  inject, signal, viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { KB, KbEntry, SUGGESTIONS, findAnswer } from './knowledge-base';

interface ChatMessage {
  role: 'user' | 'bot';
  text: string;
  entries?: KbEntry[];
  suggestions?: string[];
}

@Component({
  selector: 'app-chatbot',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './chatbot.html',
  styleUrl: './chatbot.scss',
})
export class ChatbotComponent {
  private readonly body = inject(ElementRef);

  readonly open = signal(false);
  readonly input = signal('');
  readonly messages = signal<ChatMessage[]>([]);
  readonly suggestions = SUGGESTIONS;

  private readonly scrollRef = viewChild<ElementRef>('msgList');

  readonly hasMessages = computed(() => this.messages().length > 0);

  constructor() {
    effect(() => {
      if (this.open() && !this.hasMessages()) {
        this.pushBot(
          'Hi! I\'m your offline guide for this Admin Portal. Ask me anything about using the app, or pick a topic below.',
          SUGGESTIONS.slice(0, 6),
        );
      }
    });

    effect(() => {
      // Scroll to bottom whenever messages change.
      this.messages();
      const el = this.scrollRef()?.nativeElement as HTMLElement | undefined;
      if (el) setTimeout(() => (el.scrollTop = el.scrollHeight), 30);
    });
  }

  toggle(): void { this.open.update(v => !v); }
  close(): void { this.open.set(false); }

  send(text?: string): void {
    const q = (text ?? this.input()).trim();
    if (!q) return;
    this.input.set('');

    this.messages.update(m => [...m, { role: 'user', text: q }]);

    const results = findAnswer(q);
    if (results.length === 0) {
      this.pushBot(
        'I\'m not sure about that. Try rephrasing, or choose a topic:',
        SUGGESTIONS.slice(0, 4),
      );
      return;
    }

    const [primary, ...more] = results;
    const related = primary.related
      ?.map(id => KB.find(e => e.id === id))
      .filter(Boolean) as KbEntry[] | undefined;

    this.messages.update(m => [
      ...m,
      {
        role: 'bot',
        text: primary.answer,
        entries: more.length ? more : undefined,
        suggestions: related?.map(e => e.title),
      },
    ]);
  }

  sendSuggestion(s: string): void { this.send(s); }

  private pushBot(text: string, suggestions?: string[]): void {
    this.messages.update(m => [...m, { role: 'bot', text, suggestions }]);
  }

  clearChat(): void { this.messages.set([]); }

  formatAnswer(text: string): string {
    return text
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g, '<em>$1</em>')
      .replace(/\n•/g, '<br>•')
      .replace(/\n/g, '<br>');
  }
}
