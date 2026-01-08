import { cn } from "@/lib/utils";
import { 
  LayoutDashboard, 
  TreeDeciduous, 
  BrainCircuit, 
  FileEdit, 
  Calculator, 
  Database,
  Settings,
  Lock,
  Cpu,
  Eye,
  Zap
} from "lucide-react";

interface SidebarProps {
  activeSection: string;
  onSectionChange: (section: string) => void;
}

const sections = [
  { id: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { id: "generate", label: "Generate Data", icon: Lock },
  { id: "merkle", label: "Merkle Tree", icon: TreeDeciduous },
  { id: "ai-decisions", label: "AI Decisions", icon: BrainCircuit },
  { id: "changes", label: "Changed Leaves", icon: FileEdit },
  { id: "calculations", label: "SR & C Calc", icon: Calculator },
  { id: "data", label: "Data View", icon: Database },
];

const advancedSections = [
  { id: "models", label: "Model Manager", icon: Cpu },
  { id: "encrypted", label: "Encrypted Data", icon: Eye },
  { id: "merkle-dynamic", label: "Dynamic Merkle", icon: Zap },
];

export function Sidebar({ activeSection, onSectionChange }: SidebarProps) {
  return (
    <div className="w-64 bg-card border-r border-border/50 min-h-screen p-4 space-y-2 overflow-y-auto">
      <div className="mb-8 px-2">
        <h2 className="text-xl font-bold flex items-center gap-2 text-foreground">
          <span>🦑</span> SQUID
        </h2>
        <p className="text-xs text-muted-foreground mt-1">Enhanced Dashboard</p>
      </div>

      <nav className="space-y-1">
        {sections.map((section) => {
          const Icon = section.icon;
          const isActive = activeSection === section.id;
          
          return (
            <button
              key={section.id}
              onClick={() => onSectionChange(section.id)}
              className={cn(
                "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all",
                isActive
                  ? "bg-primary text-primary-foreground shadow-md"
                  : "text-muted-foreground hover:bg-muted/50 hover:text-foreground"
              )}
            >
              <Icon className="h-4 w-4" />
              {section.label}
            </button>
          );
        })}
      </nav>

      {/* Divider */}
      <div className="pt-2 mt-4 border-t border-border/50">
        <p className="text-xs font-semibold text-muted-foreground px-3 py-2">
          ADVANCED INSPECTION
        </p>
        
        {advancedSections.map((section) => {
          const Icon = section.icon;
          const isActive = activeSection === section.id;
          
          return (
            <button
              key={section.id}
              onClick={() => onSectionChange(section.id)}
              className={cn(
                "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all",
                isActive
                  ? "bg-blue-600 text-white shadow-md"
                  : "text-muted-foreground hover:bg-blue-50 hover:text-blue-700"
              )}
            >
              <Icon className="h-4 w-4" />
              {section.label}
            </button>
          );
        })}
      </div>

      <div className="pt-4 mt-4 border-t border-border/50">
        <button
          onClick={() => onSectionChange("settings")}
          className={cn(
            "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all",
            activeSection === "settings"
              ? "bg-primary text-primary-foreground shadow-md"
              : "text-muted-foreground hover:bg-muted/50 hover:text-foreground"
          )}
        >
          <Settings className="h-4 w-4" />
          Settings
        </button>
      </div>
    </div>
  );
}
