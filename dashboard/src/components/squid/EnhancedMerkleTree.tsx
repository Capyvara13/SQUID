import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { TreeDeciduous, ZoomIn, ZoomOut, RotateCcw } from "lucide-react";

interface EnhancedMerkleTreeProps {
  data: any;
}

export function EnhancedMerkleTree({ data }: EnhancedMerkleTreeProps) {
  const [zoom, setZoom] = useState(1);
  const [selectedNode, setSelectedNode] = useState<any>(null);

  if (!data) {
    return (
      <div className="text-center py-12">
        <TreeDeciduous className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
        <p className="text-muted-foreground">No data available. Load test vectors first.</p>
      </div>
    );
  }

  const vectors = Array.isArray(data) ? data : data.test_vectors || [];

  const renderTreeNode = (node: any, level: number = 0, index: number = 0) => {
    const isLeaf = !node.left && !node.right;
    const hasChanges = node.action && node.action !== "VALID";

    return (
      <div
        key={`${level}-${index}`}
        className={`relative ${level > 0 ? 'ml-8' : ''}`}
        style={{ transform: `scale(${zoom})`, transformOrigin: 'top left' }}
      >
        <div
          className={`p-3 rounded-lg border-2 cursor-pointer transition-all hover:shadow-md ${
            isLeaf
              ? hasChanges
                ? 'border-red-300 bg-red-50'
                : 'border-green-300 bg-green-50'
              : 'border-blue-300 bg-blue-50'
          }`}
          onClick={() => setSelectedNode(node)}
        >
          <div className="flex items-center gap-2">
            <span className="font-mono text-sm">
              {node.hash ? node.hash.substring(0, 8) + '...' : 'Root'}
            </span>
            {isLeaf && (
              <Badge variant={hasChanges ? "destructive" : "secondary"} className="text-xs">
                {node.action || "VALID"}
              </Badge>
            )}
          </div>
          {node.value && (
            <div className="text-xs text-muted-foreground mt-1">
              Value: {node.value}
            </div>
          )}
        </div>

        {node.left && (
          <div className="mt-2">
            {renderTreeNode(node.left, level + 1, index * 2)}
          </div>
        )}

        {node.right && (
          <div className="mt-2">
            {renderTreeNode(node.right, level + 1, index * 2 + 1)}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="space-y-4">
      {/* Controls */}
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => setZoom(Math.max(0.5, zoom - 0.1))}
        >
          <ZoomOut className="h-4 w-4" />
        </Button>
        <span className="text-sm text-muted-foreground">
          {Math.round(zoom * 100)}%
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setZoom(Math.min(2, zoom + 0.1))}
        >
          <ZoomIn className="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setZoom(1)}
        >
          <RotateCcw className="h-4 w-4" />
        </Button>
      </div>

      {/* Tree Visualization */}
      <Card>
        <CardHeader>
          <CardTitle>Merkle Tree Structure</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            {vectors.map((vector: any, idx: number) => (
              <div key={idx} className="mb-8">
                <h3 className="text-lg font-semibold mb-4">
                  Vector {vector.id || idx + 1}
                </h3>
                <div className="border rounded-lg p-4 bg-muted/20">
                  {vector.merkle_tree ? renderTreeNode(vector.merkle_tree) : (
                    <p className="text-muted-foreground">No tree structure available</p>
                  )}
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Node Details */}
      {selectedNode && (
        <Card>
          <CardHeader>
            <CardTitle>Node Details</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              <div>
                <span className="font-medium">Hash:</span>
                <code className="ml-2 text-sm bg-muted px-2 py-1 rounded">
                  {selectedNode.hash}
                </code>
              </div>
              {selectedNode.value && (
                <div>
                  <span className="font-medium">Value:</span>
                  <code className="ml-2 text-sm bg-muted px-2 py-1 rounded">
                    {selectedNode.value}
                  </code>
                </div>
              )}
              {selectedNode.action && (
                <div>
                  <span className="font-medium">Action:</span>
                  <Badge variant={selectedNode.action === "VALID" ? "secondary" : "destructive"} className="ml-2">
                    {selectedNode.action}
                  </Badge>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
