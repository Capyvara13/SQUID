import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Loader2, Key, Shield } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

interface RealDataGeneratorProps {
  onDataGenerated: (data: any) => void;
  isLoading: boolean;
}

export function RealDataGenerator({ onDataGenerated, isLoading }: RealDataGeneratorProps) {
  const { toast } = useToast();
  const [generating, setGenerating] = useState(false);
  const [params, setParams] = useState({
    branchingFactor: "2",
    depth: "4",
    leafCount: "16",
    srTarget: "0.8",
    cTarget: "0.6"
  });

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      const response = await fetch("http://localhost:8080/api/v1/generate", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          branchingParams: {
            branchingFactor: parseInt(params.branchingFactor),
            depth: parseInt(params.depth),
            leafCount: parseInt(params.leafCount)
          },
          srTarget: parseFloat(params.srTarget),
          cTarget: parseFloat(params.cTarget)
        }),
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      onDataGenerated(data);

      toast({
        title: "Success",
        description: "Real data generated successfully with PQC encryption",
      });
    } catch (error: any) {
      toast({
        title: "Generation Failed",
        description: `Error: ${error.message}`,
        variant: "destructive",
      });
    }
    setGenerating(false);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Shield className="h-5 w-5" />
          Generate Real Data with PQC
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <Label htmlFor="branchingFactor">Branching Factor</Label>
            <Select
              value={params.branchingFactor}
              onValueChange={(value) => setParams(prev => ({ ...prev, branchingFactor: value }))}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="2">2</SelectItem>
                <SelectItem value="3">3</SelectItem>
                <SelectItem value="4">4</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div>
            <Label htmlFor="depth">Tree Depth</Label>
            <Select
              value={params.depth}
              onValueChange={(value) => setParams(prev => ({ ...prev, depth: value }))}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="3">3</SelectItem>
                <SelectItem value="4">4</SelectItem>
                <SelectItem value="5">5</SelectItem>
                <SelectItem value="6">6</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div>
            <Label htmlFor="leafCount">Leaf Count</Label>
            <Input
              id="leafCount"
              type="number"
              value={params.leafCount}
              onChange={(e) => setParams(prev => ({ ...prev, leafCount: e.target.value }))}
              min="8"
              max="64"
            />
          </div>

          <div>
            <Label htmlFor="srTarget">SR Target</Label>
            <Input
              id="srTarget"
              type="number"
              step="0.1"
              value={params.srTarget}
              onChange={(e) => setParams(prev => ({ ...prev, srTarget: e.target.value }))}
              min="0"
              max="1"
            />
          </div>

          <div>
            <Label htmlFor="cTarget">C Target</Label>
            <Input
              id="cTarget"
              type="number"
              step="0.1"
              value={params.cTarget}
              onChange={(e) => setParams(prev => ({ ...prev, cTarget: e.target.value }))}
              min="0"
              max="1"
            />
          </div>
        </div>

        <div className="pt-4">
          <Button
            onClick={handleGenerate}
            disabled={generating || isLoading}
            className="w-full"
          >
            {generating ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
                Generating...
              </>
            ) : (
              <>
                <Key className="h-4 w-4 mr-2" />
                Generate Real Data
              </>
            )}
          </Button>
        </div>

        <div className="text-sm text-muted-foreground">
          <p>This will generate real cryptographic data using Post-Quantum Cryptography (PQC) algorithms,
          create a Merkle tree, and apply AI-driven modifications to achieve the target SR and C values.</p>
        </div>
      </CardContent>
    </Card>
  );
}
