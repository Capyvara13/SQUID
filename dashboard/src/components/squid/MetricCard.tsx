interface MetricCardProps {
  label: string;
  value: string | number;
  subtitle: string;
}

export function MetricCard({ label, value, subtitle }: MetricCardProps) {
  return (
    <div className="bg-primary/10 p-5 rounded-lg border border-primary/20">
      <div className="text-sm font-medium text-muted-foreground mb-1">{label}</div>
      <div className="text-3xl font-bold text-foreground mb-1">{value}</div>
      <div className="text-xs text-muted-foreground">{subtitle}</div>
    </div>
  );
}
