import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

interface Model {
  id: string;
  version: string;
  is_active: boolean;
  architecture?: string;
  hash?: string;
  loss?: number;
  accuracy?: number;
  f1_score?: number;
  created_at?: number;
  trained_at?: number;
  description?: string;
}

interface HistoryEvent {
  id: string;
  timestamp: number;
  action: string;
  from_version?: string;
  to_version?: string;
  version?: string;
  reason?: string;
  initiator?: string;
  details?: string;
}

export function ModelManager({ apiBaseUrl = "http://localhost:8080/api/v1" }) {
  const [models, setModels] = useState<Model[]>([]);
  const [history, setHistory] = useState<HistoryEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeModel, setActiveModel] = useState<string | null>(null);
  const [switchingTo, setSwitchingTo] = useState<string | null>(null);

  // Load models
  useEffect(() => {
    fetchModels();
    fetchHistory();
  }, []);

  const fetchModels = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetch(`${apiBaseUrl}/models/list`);
      const data = await response.json();
      setModels(data.models || []);
      setActiveModel(data.active_model);
    } catch (err) {
      setError(`Error fetching models: ${err}`);
    } finally {
      setLoading(false);
    }
  };

  const fetchHistory = async () => {
    try {
      const response = await fetch(`${apiBaseUrl}/models/history/all`);
      const data = await response.json();
      setHistory(data || []);
    } catch (err) {
      console.error("Error fetching history:", err);
    }
  };

  const handleSwitchModel = async (version: string) => {
    try {
      setSwitchingTo(version);
      const response = await fetch(`${apiBaseUrl}/models/switch`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          version,
          reason: "Manual switch from dashboard",
          initiator: "dashboard-user"
        })
      });

      if (response.ok) {
        setActiveModel(version);
        await fetchModels();
        await fetchHistory();
        setError(null);
      } else {
        setError(`Failed to switch model: ${response.statusText}`);
      }
    } catch (err) {
      setError(`Error switching model: ${err}`);
    } finally {
      setSwitchingTo(null);
    }
  };

  return (
    <div className="w-full space-y-4">
      {error && (
        <Alert className="bg-red-50 border-red-200">
          <AlertDescription className="text-red-800">{error}</AlertDescription>
        </Alert>
      )}

      <Tabs defaultValue="models" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="models">Models</TabsTrigger>
          <TabsTrigger value="history">History</TabsTrigger>
        </TabsList>

        {/* Models Tab */}
        <TabsContent value="models" className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {models.map((model) => (
              <Card 
                key={model.id}
                className={`cursor-pointer transition-all ${
                  model.is_active 
                    ? 'border-green-500 bg-green-50' 
                    : 'border-gray-200'
                }`}
              >
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-lg">{model.version}</CardTitle>
                    {model.is_active && (
                      <Badge className="bg-green-500">Active</Badge>
                    )}
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  {model.description && (
                    <p className="text-sm text-gray-600">{model.description}</p>
                  )}
                  
                  {model.architecture && (
                    <div>
                      <p className="text-xs font-semibold text-gray-500">Architecture</p>
                      <p className="text-xs font-mono text-gray-700">{model.architecture}</p>
                    </div>
                  )}

                  {/* Metrics Grid */}
                  <div className="grid grid-cols-3 gap-2 pt-2 border-t">
                    {model.accuracy !== undefined && (
                      <div>
                        <p className="text-xs text-gray-500">Accuracy</p>
                        <p className="text-sm font-bold text-blue-600">
                          {(model.accuracy * 100).toFixed(1)}%
                        </p>
                      </div>
                    )}
                    {model.f1_score !== undefined && (
                      <div>
                        <p className="text-xs text-gray-500">F1 Score</p>
                        <p className="text-sm font-bold text-purple-600">
                          {(model.f1_score * 100).toFixed(1)}%
                        </p>
                      </div>
                    )}
                    {model.loss !== undefined && (
                      <div>
                        <p className="text-xs text-gray-500">Loss</p>
                        <p className="text-sm font-bold text-orange-600">
                          {model.loss.toFixed(3)}
                        </p>
                      </div>
                    )}
                  </div>

                  {model.hash && (
                    <div>
                      <p className="text-xs font-semibold text-gray-500">Hash</p>
                      <p className="text-xs font-mono text-gray-500 truncate">
                        {model.hash.substring(0, 16)}...
                      </p>
                    </div>
                  )}

                  {/* Action Button */}
                  {!model.is_active && (
                    <Button
                      size="sm"
                      onClick={() => handleSwitchModel(model.version)}
                      disabled={switchingTo !== null}
                      className="w-full mt-2"
                    >
                      {switchingTo === model.version ? "Switching..." : "Switch to this model"}
                    </Button>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>

          {models.length === 0 && !loading && (
            <Card>
              <CardContent className="py-8 text-center text-gray-500">
                No models found
              </CardContent>
            </Card>
          )}
        </TabsContent>

        {/* History Tab */}
        <TabsContent value="history" className="space-y-4">
          <div className="space-y-2">
            {history.length > 0 ? (
              history.slice().reverse().map((event, idx) => (
                <Card key={idx} className="p-4">
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <Badge variant="outline">{event.action}</Badge>
                        <span className="text-xs text-gray-500">
                          {new Date(event.timestamp).toLocaleString()}
                        </span>
                      </div>
                      
                      {event.action === "SWITCH" && (
                        <p className="text-sm text-gray-700">
                          <span className="font-semibold">{event.from_version}</span>
                          {" → "}
                          <span className="font-semibold text-green-600">{event.to_version}</span>
                        </p>
                      )}
                      
                      {event.action === "REGISTER" && (
                        <p className="text-sm text-gray-700">
                          Registered version <span className="font-semibold">{event.version}</span>
                        </p>
                      )}

                      {event.reason && (
                        <p className="text-xs text-gray-600 mt-1">{event.reason}</p>
                      )}
                      
                      {event.initiator && (
                        <p className="text-xs text-gray-500 mt-1">By: {event.initiator}</p>
                      )}
                    </div>
                  </div>
                </Card>
              ))
            ) : (
              <Card>
                <CardContent className="py-8 text-center text-gray-500">
                  No history available
                </CardContent>
              </Card>
            )}
          </div>
        </TabsContent>
      </Tabs>

      {/* Auto-refresh */}
      <div className="text-xs text-gray-500 text-right pt-4">
        Last updated: {new Date().toLocaleTimeString()}
      </div>
    </div>
  );
}
