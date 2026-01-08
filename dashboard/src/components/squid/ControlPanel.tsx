import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Loader2, RefreshCw, Play, Square, BarChart3, Trash2 } from "lucide-react";

interface ControlPanelProps {
  onCheckServices: () => void;
  onStartDocker: () => void;
  onStopDocker: () => void;
  onAnalyze: () => void;
  onClear: () => void;
  isLoading: boolean;
}

export function ControlPanel({
  onCheckServices,
  onStartDocker,
  onStopDocker,
  onAnalyze,
  onClear,
  isLoading,
}: ControlPanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <span>🎛️</span> Control Panel
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          <Button
            onClick={onCheckServices}
            disabled={isLoading}
            className="flex items-center gap-2"
          >
            {isLoading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <RefreshCw className="h-4 w-4" />
            )}
            Check Services
          </Button>

          <Button
            onClick={onStartDocker}
            variant="outline"
            className="flex items-center gap-2"
          >
            <Play className="h-4 w-4" />
            Start Docker
          </Button>

          <Button
            onClick={onStopDocker}
            variant="outline"
            className="flex items-center gap-2"
          >
            <Square className="h-4 w-4" />
            Stop Docker
          </Button>

          <Button
            onClick={onAnalyze}
            variant="secondary"
            className="flex items-center gap-2"
          >
            <BarChart3 className="h-4 w-4" />
            Analyze Data
          </Button>

          <Button
            onClick={onClear}
            variant="destructive"
            className="flex items-center gap-2"
          >
            <Trash2 className="h-4 w-4" />
            Clear Display
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
