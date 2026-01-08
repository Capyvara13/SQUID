import { cn } from "@/lib/utils";

interface AIDecisionsProps {
  data: any;
}

const actionColors: Record<string, string> = {
  VALID: "bg-success/20 border-success text-success-foreground",
  DECOY: "bg-warning/20 border-warning text-warning-foreground",
  MUTATE: "bg-destructive/20 border-destructive text-destructive-foreground",
  REASSIGN: "bg-primary/20 border-primary text-primary-foreground",
};

export function AIDecisions({ data }: AIDecisionsProps) {
  if (!data) {
    return (
      <div className="bg-card p-6 rounded-lg border border-border/50 shadow-sm">
        <h2 className="text-lg font-semibold mb-4 flex items-center gap-2 text-foreground">
          <span>🤖</span> AI Decisions
        </h2>
        <div className="bg-muted/30 rounded-lg p-12 text-center">
          <p className="text-muted-foreground">
            No decisions available. Fetch test vectors first.
          </p>
        </div>
      </div>
    );
  }

  const vectors = Array.isArray(data) ? data : data.test_vectors || [];
  const allActions: string[] = [];
  
  vectors.forEach((v: any) => {
    if (v.actions) allActions.push(...v.actions);
  });

  const actionCounts: Record<string, number> = {};
  allActions.forEach(action => {
    actionCounts[action] = (actionCounts[action] || 0) + 1;
  });

  return (
    <div className="bg-card p-6 rounded-lg border border-border/50 shadow-sm">
      <h2 className="text-lg font-semibold mb-4 flex items-center gap-2 text-foreground">
        <span>🤖</span> AI Decisions
      </h2>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {Object.entries(actionCounts).map(([action, count]) => (
          <div
            key={action}
            className={cn(
              "p-4 rounded-lg border-2 text-center transition-transform hover:-translate-y-1",
              actionColors[action] || "bg-muted/20 border-muted"
            )}
          >
            <div className="font-bold text-2xl mb-1">{count}</div>
            <div className="text-xs font-medium uppercase">{action}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
