import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { ChevronDown, ChevronRight, Database, Code, BarChart3 } from "lucide-react";

interface DataViewProps {
  rawData: any;
  javaData: any;
  pythonData: any;
  analysisData: any;
}

export function DataView({ rawData, javaData, pythonData, analysisData }: DataViewProps) {
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());

  const toggleExpanded = (id: string) => {
    const newExpanded = new Set(expandedItems);
    if (newExpanded.has(id)) {
      newExpanded.delete(id);
    } else {
      newExpanded.add(id);
    }
    setExpandedItems(newExpanded);
  };

  const renderVectorCard = (vector: any, index: number, source: string) => {
    const id = `${source}-${index}`;
    const isExpanded = expandedItems.has(id);

    return (
      <Card key={id} className="mb-4">
        <Collapsible open={isExpanded} onOpenChange={() => toggleExpanded(id)}>
          <CollapsibleTrigger asChild>
            <CardHeader className="cursor-pointer hover:bg-muted/50">
              <div className="flex items-center justify-between">
                <CardTitle className="text-lg flex items-center gap-2">
                  <Button variant="ghost" size="sm" className="p-0 h-auto">
                    {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                  </Button>
                  Vector {vector.id || index + 1}
                  <Badge variant="outline">{source}</Badge>
                </CardTitle>
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <span>SR: {vector.sr?.toFixed(6) || 'N/A'}</span>
                  <span>C: {vector.c?.toFixed(6) || 'N/A'}</span>
                </div>
              </div>
            </CardHeader>
          </CollapsibleTrigger>
          <CollapsibleContent>
            <CardContent className="pt-0">
              <Tabs defaultValue="overview" className="w-full">
                <TabsList className="grid w-full grid-cols-3">
                  <TabsTrigger value="overview">Overview</TabsTrigger>
                  <TabsTrigger value="actions">Actions</TabsTrigger>
                  <TabsTrigger value="raw">Raw Data</TabsTrigger>
                </TabsList>

                <TabsContent value="overview" className="space-y-4">
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    <div>
                      <div className="text-sm font-medium text-muted-foreground">ID</div>
                      <div className="font-mono text-sm">{vector.id || 'N/A'}</div>
                    </div>
                    <div>
                      <div className="text-sm font-medium text-muted-foreground">SR Value</div>
                      <div className="font-mono text-sm">{vector.sr?.toFixed(6) || 'N/A'}</div>
                    </div>
                    <div>
                      <div className="text-sm font-medium text-muted-foreground">C Value</div>
                      <div className="font-mono text-sm">{vector.c?.toFixed(6) || 'N/A'}</div>
                    </div>
                    <div>
                      <div className="text-sm font-medium text-muted-foreground">Leaves</div>
                      <div className="font-mono text-sm">{vector.actions?.length || 0}</div>
                    </div>
                  </div>

                  {vector.params && (
                    <div>
                      <div className="text-sm font-medium text-muted-foreground mb-2">Parameters</div>
                      <pre className="text-xs bg-muted p-2 rounded overflow-x-auto">
                        {JSON.stringify(vector.params, null, 2)}
                      </pre>
                    </div>
                  )}

                  {vector.merkle_root_hex && (
                    <div>
                      <div className="text-sm font-medium text-muted-foreground mb-2">Merkle Root</div>
                      <code className="text-xs bg-muted p-2 rounded block break-all">
                        {vector.merkle_root_hex}
                      </code>
                    </div>
                  )}
                </TabsContent>

                <TabsContent value="actions" className="space-y-4">
                  {vector.actions ? (
                    <div>
                      <div className="text-sm font-medium text-muted-foreground mb-2">
                        Action Distribution
                      </div>
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-2 mb-4">
                        {['VALID', 'DECOY', 'MUTATE', 'REASSIGN'].map(action => {
                          const count = vector.actions.filter((a: string) => a === action).length;
                          return (
                            <div key={action} className="text-center p-2 bg-muted rounded">
                              <div className="font-bold">{count}</div>
                              <div className="text-xs text-muted-foreground">{action}</div>
                            </div>
                          );
                        })}
                      </div>

                      <div className="text-sm font-medium text-muted-foreground mb-2">
                        Action Sequence
                      </div>
                      <div className="flex flex-wrap gap-1">
                        {vector.actions.map((action: string, idx: number) => (
                          <Badge
                            key={idx}
                            variant={action === 'VALID' ? 'secondary' : 'destructive'}
                            className="text-xs"
                          >
                            {idx + 1}: {action}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  ) : (
                    <p className="text-muted-foreground">No actions data available</p>
                  )}
                </TabsContent>

                <TabsContent value="raw" className="space-y-4">
                  <pre className="text-xs bg-muted p-4 rounded overflow-x-auto max-h-96 overflow-y-auto">
                    {JSON.stringify(vector, null, 2)}
                  </pre>
                </TabsContent>
              </Tabs>
            </CardContent>
          </CollapsibleContent>
        </Collapsible>
      </Card>
    );
  };

  const getDataArrays = () => {
    const arrays = [];
    if (rawData) arrays.push({ data: Array.isArray(rawData) ? rawData : [rawData], label: 'Raw Data' });
    if (javaData) arrays.push({ data: Array.isArray(javaData) ? javaData : [javaData], label: 'Java Backend' });
    if (pythonData) arrays.push({ data: Array.isArray(pythonData) ? pythonData : [pythonData], label: 'Python AI' });
    return arrays;
  };

  const dataArrays = getDataArrays();

  return (
    <div className="space-y-6">
      {/* Summary */}
      {analysisData && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BarChart3 className="h-5 w-5" />
              Analysis Summary
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="text-center">
                <div className="text-2xl font-bold">{analysisData.summary?.total_vectors || 0}</div>
                <div className="text-sm text-muted-foreground">Total Vectors</div>
              </div>
              <div className="text-center">
                <div className="text-2xl font-bold">
                  {analysisData.vectors?.reduce((sum: number, v: any) => sum + (v.total_leaves || 0), 0) || 0}
                </div>
                <div className="text-sm text-muted-foreground">Total Leaves</div>
              </div>
              <div className="text-center">
                <div className="text-2xl font-bold">
                  {new Date(analysisData.summary?.timestamp).toLocaleString() || 'N/A'}
                </div>
                <div className="text-sm text-muted-foreground">Analysis Time</div>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Data Sources */}
      <Tabs defaultValue={dataArrays[0]?.label || 'Raw Data'} className="w-full">
        <TabsList className="grid w-full grid-cols-3">
          {dataArrays.map(({ label }) => (
            <TabsTrigger key={label} value={label}>
              {label}
            </TabsTrigger>
          ))}
        </TabsList>

        {dataArrays.map(({ data, label }) => (
          <TabsContent key={label} value={label} className="space-y-4">
            <div className="flex items-center gap-2 mb-4">
              <Database className="h-5 w-5" />
              <h3 className="text-lg font-semibold">{label}</h3>
              <Badge variant="outline">{data.length} vectors</Badge>
            </div>

            {data.length === 0 ? (
              <Card>
                <CardContent className="text-center py-8">
                  <Code className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
                  <p className="text-muted-foreground">No data available for {label}</p>
                </CardContent>
              </Card>
            ) : (
              data.map((vector: any, index: number) =>
                renderVectorCard(vector, index, label)
              )
            )}
          </TabsContent>
        ))}
      </Tabs>
    </div>
  );
}
