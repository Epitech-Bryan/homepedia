import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import type { TransactionSummary } from "@/api/client";

interface TransactionDetailDialogProps {
  transaction: TransactionSummary | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between items-baseline py-2 border-b border-border last:border-b-0">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium text-right">{children}</dd>
    </div>
  );
}

export function TransactionDetailDialog({
  transaction,
  open,
  onOpenChange,
}: TransactionDetailDialogProps) {
  if (!transaction) return null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Transaction Details</DialogTitle>
        </DialogHeader>
        <dl className="mt-2">
          <DetailRow label="Price">
            {transaction.propertyValue?.toLocaleString("fr-FR")} €
          </DetailRow>
          <DetailRow label="Property Type">
            <Badge>{transaction.propertyType}</Badge>
          </DetailRow>
          <DetailRow label="City">
            {transaction.cityName} ({transaction.cityInseeCode})
          </DetailRow>
          <DetailRow label="Date">{transaction.mutationDate}</DetailRow>
          <DetailRow label="Built Surface">
            {transaction.builtSurface > 0
              ? `${transaction.builtSurface.toLocaleString("fr-FR")} m²`
              : "—"}
          </DetailRow>
          <DetailRow label="Land Surface">
            {transaction.landSurface > 0
              ? `${transaction.landSurface.toLocaleString("fr-FR")} m²`
              : "—"}
          </DetailRow>
          <DetailRow label="Rooms">
            {transaction.roomCount > 0 ? transaction.roomCount.toLocaleString("fr-FR") : "—"}
          </DetailRow>
        </dl>
      </DialogContent>
    </Dialog>
  );
}
