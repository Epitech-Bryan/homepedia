import { Card, CardContent } from "@/components/ui/card";

export function ErrorMessage({ message }: { message: string }) {
  return (
    <Card className="border-destructive/50 bg-destructive/5">
      <CardContent className="pt-6">
        <p className="font-medium text-destructive">Error</p>
        <p className="text-sm text-destructive/80 mt-1">{message}</p>
      </CardContent>
    </Card>
  );
}
