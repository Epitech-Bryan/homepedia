import { useMemo, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface WordCloudProps {
  words: Record<string, number>;
}

const COLORS = [
  "hsl(var(--primary))",
  "hsl(var(--secondary-foreground))",
  "hsl(var(--accent-foreground))",
  "hsl(var(--muted-foreground))",
];

function seededRandom(seed: number) {
  const x = Math.sin(seed + 1) * 10000;
  return x - Math.floor(x);
}

export function WordCloud({ words }: WordCloudProps) {
  const [highlighted, setHighlighted] = useState<string | null>(null);

  const entries = useMemo(() => {
    const sorted = Object.entries(words).sort((a, b) => b[1] - a[1]);
    if (sorted.length === 0) return [];

    const maxFreq = sorted[0][1];
    const minFreq = sorted[sorted.length - 1][1];
    const range = maxFreq - minFreq || 1;

    return sorted.map(([word, freq], i) => {
      const normalized = (freq - minFreq) / range;
      const fontSize = 0.75 + normalized * 2;
      const rotation = (seededRandom(i) - 0.5) * 30;
      const color = COLORS[i % COLORS.length];
      return { word, freq, fontSize, rotation, color };
    });
  }, [words]);

  if (entries.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Word Cloud</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm">No data available</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Word Cloud</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-wrap items-center justify-center gap-3 py-4">
          {entries.map(({ word, freq, fontSize, rotation, color }) => (
            <span
              key={word}
              className="cursor-pointer transition-all duration-200 hover:scale-110"
              style={{
                fontSize: `${fontSize}rem`,
                color: highlighted === word ? "hsl(var(--primary))" : color,
                transform: `rotate(${rotation}deg)`,
                opacity: highlighted && highlighted !== word ? 0.4 : 1,
                fontWeight: fontSize > 1.5 ? 700 : 500,
              }}
              title={`${word}: ${freq}`}
              onClick={() => setHighlighted(highlighted === word ? null : word)}
            >
              {word}
            </span>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
