interface SRCalculationsProps {
  data: any;
}

export function SRCalculations({ data }: SRCalculationsProps) {
  if (!data) {
    return (
      <div className="bg-card p-6 rounded-lg border border-border/50 shadow-sm">
        <h2 className="text-lg font-semibold mb-4 flex items-center gap-2 text-foreground">
          <span>🧮</span> SR & C Calculations
        </h2>
        <div className="bg-muted/30 rounded-lg p-8 text-center">
          <p className="text-muted-foreground">No calculations available yet.</p>
        </div>
      </div>
    );
  }

  const vectors = Array.isArray(data) ? data : data.test_vectors || [];

  return (
    <div className="bg-card p-6 rounded-lg border border-border/50 shadow-sm">
      <h2 className="text-lg font-semibold mb-4 flex items-center gap-2 text-foreground">
        <span>🧮</span> SR & C Calculations
      </h2>
      <div className="space-y-4 max-h-72 overflow-y-auto">
        {vectors.map((vector: any, idx: number) => {
          const params = vector.params || {};
          return (
            <div key={idx} className="bg-muted/30 p-4 rounded-lg font-mono text-xs space-y-1">
              <div className="font-bold text-sm text-foreground mb-2">
                Test Vector: {vector.id || `Vector ${idx + 1}`}
              </div>
              <div className="text-muted-foreground">
                <strong>Parameters:</strong> b={params.b}, m={params.m}, t={params.t}
              </div>
              <div className="text-muted-foreground">
                <strong>SR (Super-Relation):</strong>{" "}
                {Number.isFinite(vector.sr) ? Number(vector.sr).toFixed(6) : "N/A"}
              </div>
              <div className="text-muted-foreground">
                <strong>C (Correlation):</strong>{" "}
                {Number.isFinite(vector.c) ? Number(vector.c).toFixed(6) : "N/A"}
              </div>
              <div className="text-muted-foreground">
                <strong>Total Leaves:</strong>{" "}
                {params.b && params.m ? Math.pow(params.b, params.m) : "N/A"}
              </div>
              <div className="text-muted-foreground truncate">
                <strong>Model Hash:</strong> {vector.model_hash || "N/A"}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
