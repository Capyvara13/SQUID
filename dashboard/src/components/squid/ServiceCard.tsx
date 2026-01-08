import { Circle } from "lucide-react";
import { cn } from "@/lib/utils";

interface ServiceCardProps {
  name: string;
  detail: string;
  status: "online" | "offline" | "checking";
  lastCheck?: string;
}

const statusConfig = {
  online: {
    color: "bg-success",
    text: "Online and responding",
  },
  offline: {
    color: "bg-destructive",
    text: "Offline",
  },
  checking: {
    color: "bg-warning",
    text: "Checking...",
  },
};

export function ServiceCard({ name, detail, status, lastCheck }: ServiceCardProps) {
  const config = statusConfig[status];

  return (
    <div className="bg-card p-5 rounded-lg border border-border/50 shadow-sm">
      <div className="flex items-center gap-2 mb-2">
        <Circle
          className={cn(
            "h-3 w-3 fill-current",
            config.color,
            status === "online" && "animate-pulse-glow"
          )}
        />
        <h3 className="font-semibold text-foreground">{name}</h3>
      </div>
      <p className="text-sm text-muted-foreground">{config.text}</p>
      {lastCheck && (
        <p className="text-xs text-muted-foreground mt-1">Last checked: {lastCheck}</p>
      )}
      <p className="text-xs text-muted-foreground mt-1">{detail}</p>
    </div>
  );
}
