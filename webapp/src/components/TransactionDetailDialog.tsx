import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { useTransactionDetail } from "@/api/hooks";

interface TransactionDetailDialogProps {
  transactionId: number | null;
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

function formatAddress(
  streetNumber: string | null,
  streetType: string | null,
  postalCode: string | null,
  cityName: string,
): string {
  const parts = [streetNumber, streetType].filter(Boolean).join(" ");
  const cityPart = [postalCode, cityName].filter(Boolean).join(" ");
  return parts ? `${parts}, ${cityPart}` : cityPart;
}

export function TransactionDetailDialog({
  transactionId,
  open,
  onOpenChange,
}: TransactionDetailDialogProps) {
  const { data: tx, isLoading } = useTransactionDetail(open ? transactionId : null);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Détails de la transaction</DialogTitle>
        </DialogHeader>

        {isLoading || !tx ? (
          <div className="flex justify-center py-8">
            <LoadingSpinner />
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-2xl font-bold">
                {tx.propertyValue?.toLocaleString("fr-FR")} €
              </span>
              <Badge>{tx.propertyType}</Badge>
            </div>

            {tx.pricePerSqm != null && (
              <p className="text-sm text-muted-foreground">
                {Math.round(tx.pricePerSqm).toLocaleString("fr-FR")} €/m²
              </p>
            )}

            <dl>
              <DetailRow label="Adresse">
                {formatAddress(tx.streetNumber, tx.streetType, tx.postalCode, tx.cityName)}
              </DetailRow>
              <DetailRow label="Date">{tx.mutationDate}</DetailRow>
              <DetailRow label="Nature">{tx.mutationNature}</DetailRow>
              <DetailRow label="Surface bâtie">
                {tx.builtSurface > 0 ? `${tx.builtSurface.toLocaleString("fr-FR")} m²` : "—"}
              </DetailRow>
              <DetailRow label="Surface terrain">
                {tx.landSurface > 0 ? `${tx.landSurface.toLocaleString("fr-FR")} m²` : "—"}
              </DetailRow>
              <DetailRow label="Pièces">
                {tx.roomCount > 0 ? tx.roomCount.toLocaleString("fr-FR") : "—"}
              </DetailRow>
              {tx.lotCount != null && tx.lotCount > 0 && (
                <DetailRow label="Lots">{tx.lotCount}</DetailRow>
              )}
              {tx.section && (
                <DetailRow label="Section cadastrale">
                  {tx.section}
                  {tx.planNumber ? ` / ${tx.planNumber}` : ""}
                </DetailRow>
              )}
              {tx.departmentCode && <DetailRow label="Département">{tx.departmentCode}</DetailRow>}
              <DetailRow label="Code INSEE">{tx.cityInseeCode}</DetailRow>
            </dl>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
