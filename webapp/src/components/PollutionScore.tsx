import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

// GES (greenhouse-gas) class scale, best (A) to worst (G). The backend score is
// a surface-weighted 1..7 mean over the area's communes; we round it to the
// nearest class for the letter while still showing the precise value.
const GES_LETTERS = ["A", "B", "C", "D", "E", "F", "G"] as const;
// Diverging green→red ramp, aligned with the map choropleth's pollution scale.
const GES_COLORS = [
  "#1a9850",
  "#66bd63",
  "#a6d96a",
  "#fee08b",
  "#fdae61",
  "#f46d43",
  "#d73027",
] as const;

export function PollutionScore({ score }: { score: number | null | undefined }) {
  if (score == null || !Number.isFinite(score)) return null;
  const idx = Math.min(6, Math.max(0, Math.round(score) - 1));
  const letter = GES_LETTERS[idx];
  const color = GES_COLORS[idx];

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Pollution — GES</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex items-center gap-3">
          <span
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-lg font-bold text-white"
            style={{ backgroundColor: color }}
            aria-label={`Classe GES ${letter}`}
          >
            {letter}
          </span>
          <div>
            <p className="text-sm font-medium">
              Classe {letter}
              <span className="text-muted-foreground font-normal"> · {score.toFixed(2)}/7</span>
            </p>
            <p className="text-xs text-muted-foreground">
              Émissions de gaz à effet de serre (moyenne pondérée des logements, source ADEME/DPE).
            </p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
