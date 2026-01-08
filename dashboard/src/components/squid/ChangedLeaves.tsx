import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { AlertTriangle, CheckCircle } from "lucide-react";

interface ChangedLeavesProps {
  data: any;
}

export function ChangedLeaves({ data }: ChangedLeavesProps) {
  if (!data) {
    return (
      <div className="text-center py-12">
        <AlertTriangle className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
        <p className="text-muted-foreground">No data available. Load test vectors first.</p>
      </div>
    );
  }

  const vectors = Array.isArray(data) ? data : data.test_vectors || [];

  const changedLeaves = vectors.flatMap((vector: any, vectorIdx: number) => {
    if (!vector.actions) return [];

    return vector.actions
      .map((action: string, leafIdx: number) => ({
        vectorId: vector.id || vectorIdx + 1,
        vectorIdx,
        leafIdx,
        action,
        originalValue: vector.leaves?.[leafIdx] || 'N/A',
        isChanged: action !== "VALID"
      }))
      .filter(item => item.isChanged);
  });

  const totalLeaves = vectors.reduce((sum, vector) => sum + (vector.actions?.length || 0), 0);
  const changedCount = changedLeaves.length;
  const changePercentage = totalLeaves > 0 ? ((changedCount / totalLeaves) * 100).toFixed(2) : "0.00";

  return (
    <div className="space-y-6">
      {/* Summary */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-orange-500" />
            Changed Leaves Summary
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-red-600">{changedCount}</div>
              <div className="text-sm text-muted-foreground">Changed Leaves</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-600">{totalLeaves}</div>
              <div className="text-sm text-muted-foreground">Total Leaves</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600">{changePercentage}%</div>
              <div className="text-sm text-muted-foreground">Change Rate</div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Changed Leaves List */}
      <Card>
        <CardHeader>
          <CardTitle>Changed Leaves Details</CardTitle>
        </CardHeader>
        <CardContent>
          {changedLeaves.length === 0 ? (
            <div className="text-center py-8">
              <CheckCircle className="h-12 w-12 mx-auto text-green-500 mb-4" />
              <p className="text-muted-foreground">No leaves have been changed. All leaves are valid.</p>
            </div>
          ) : (
            <div className="space-y-4">
              {changedLeaves.map((leaf, idx) => (
                <div key={idx} className="border rounded-lg p-4 bg-red-50 border-red-200">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">Vector {leaf.vectorId}</span>
                      <Badge variant="destructive">{leaf.action}</Badge>
                    </div>
                    <span className="text-sm text-muted-foreground">
                      Leaf #{leaf.leafIdx + 1}
                    </span>
                  </div>
                  <div className="text-sm text-muted-foreground">
                    Original Value: <code className="bg-muted px-2 py-1 rounded text-xs">
                      {leaf.originalValue}
                    </code>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Action Distribution */}
      <Card>
        <CardHeader>
          <CardTitle>Action Distribution</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {["DECOY", "MUTATE", "REASSIGN"].map(action => {
              const count = changedLeaves.filter(leaf => leaf.action === action).length;
              return (
                <div key={action} className="text-center">
                  <div className="text-2xl font-bold text-orange-600">{count}</div>
                  <div className="text-sm text-muted-foreground">{action}</div>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
