import { useState, useEffect } from "react";
import { Sidebar } from "@/components/squid/Sidebar";
import { ServiceCard } from "@/components/squid/ServiceCard";
import { MetricCard } from "@/components/squid/MetricCard";
import { ControlPanel } from "@/components/squid/ControlPanel";
import { EnhancedMerkleTree } from "@/components/squid/EnhancedMerkleTree";
import { AIDecisions } from "@/components/squid/AIDecisions";
import { SRCalculations } from "@/components/squid/SRCalculations";
import { ChangedLeaves } from "@/components/squid/ChangedLeaves";
import { RealDataGenerator } from "@/components/squid/RealDataGenerator";
import { DataView } from "@/components/squid/DataView";
import { ModelManager } from "@/components/squid/ModelManager";
import { EncryptedDataViewer } from "@/components/squid/EncryptedDataViewer";
import { DynamicMerkleTreeViewer } from "@/components/squid/DynamicMerkleTreeViewer";
import { useToast } from "@/hooks/use-toast";

const Index = () => {
  const { toast } = useToast();
  const [activeSection, setActiveSection] = useState("dashboard");
  const [isLoading, setIsLoading] = useState(false);
  const [serviceStatus, setServiceStatus] = useState({
    java: "offline" as "online" | "offline" | "checking",
    python: "offline" as "online" | "offline" | "checking",
    docker: "offline" as "online" | "offline" | "checking",
  });
  
  const [currentData, setCurrentData] = useState<any>(null);
  const [javaData, setJavaData] = useState<any>(null);
  const [pythonData, setPythonData] = useState<any>(null);
  const [pythonInfo, setPythonInfo] = useState<any>(null);
  const [analysisData, setAnalysisData] = useState<any>(null);
  const [realGeneratedData, setRealGeneratedData] = useState<any>(null);
  
  const normalizeData = (data: any) => {
    if (data.analysis) {
      return {
        id: data.id || 'generated',
        params: data.analysis.tree_params || data.params,
        sr: data.analysis.sr,
        c: data.analysis.c,
        actions: data.analysis.actions,
        merkle_root_hex: data.merkle_root || data.merkleRoot,
        leaves: data.analysis.leaves_detail,
        ciphertext: data.ciphertext,
        signature: data.signature,
        seed_model_hash: data.seed_model_hash,
        timestamp: data.timestamp,
      };
    }
    return data;
  };
  
  const [metrics, setMetrics] = useState({
    vectors: "--",
    leaves: "--",
    changed: "--",
    avgSR: "--",
  });

  const checkServices = async () => {
    setIsLoading(true);
    
    // Check Java
    try {
      const response = await fetch("http://localhost:8080/api/v1/health");
      if (response.ok) {
        setServiceStatus(prev => ({ ...prev, java: "online" }));
        toast({ title: "Java Backend Online", description: "Port 8080 responding" });
      }
    } catch {
      setServiceStatus(prev => ({ ...prev, java: "offline" }));
    }

    // Check Python
    try {
      const response = await fetch("http://localhost:5000/health");
      if (response.ok) {
        setServiceStatus(prev => ({ ...prev, python: "online" }));
        toast({ title: "Python AI Service Online", description: "Port 5000 responding" });

        // Fetch model info to know whether model was loaded
        try {
          const infoResp = await fetch("http://localhost:5000/model/info");
          if (infoResp.ok) {
            const info = await infoResp.json();
            setPythonInfo(info);
          }
        } catch {}
      }
    } catch {
      setServiceStatus(prev => ({ ...prev, python: "offline" }));
    }

    setIsLoading(false);
  };

  // Run service checks automatically on first render
  useEffect(() => {
    checkServices();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchTestVectors = async () => {
    if (serviceStatus.java !== "online") {
      toast({
        title: "Service Not Available",
        description: "Java backend is not running. Please start the services first.",
        variant: "destructive",
      });
      return;
    }

    setIsLoading(true);
    try {
      const response = await fetch("http://localhost:8080/api/v1/testvectors");
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      
      const data = await response.json();
      setCurrentData(data);
      setJavaData(data);
      updateMetrics(data);
      
      toast({ title: "Success", description: "Test vectors loaded successfully" });

      // Try to fetch Python data too
      if (serviceStatus.python === "online") {
        try {
          const pyResponse = await fetch("http://localhost:5000/test/vectors");
          if (pyResponse.ok) {
            const pyData = await pyResponse.json();
            setPythonData(pyData);
          }
        } catch {}
      }
    } catch (error: any) {
      toast({
        title: "Error",
        description: `Failed to fetch test vectors: ${error.message}`,
        variant: "destructive",
      });
    }
    setIsLoading(false);
  };

  const updateMetrics = (data: any) => {
    const vectors = Array.isArray(data) ? data : data.test_vectors || [];
    
    let totalLeaves = 0;
    let totalChanged = 0;
    let totalSR = 0;
    let validSRCount = 0;

    vectors.forEach((v: any) => {
      if (v.actions) {
        totalLeaves += v.actions.length;
        totalChanged += v.actions.filter((a: string) => a !== "VALID").length;
      }
      if (typeof v.sr === "number" && isFinite(v.sr)) {
        totalSR += v.sr;
        validSRCount += 1;
      }
    });

    setMetrics({
      vectors: vectors.length.toString(),
      leaves: totalLeaves.toString(),
      changed: totalChanged.toString(),
      avgSR: validSRCount > 0 ? (totalSR / validSRCount).toFixed(6) : "N/A",
    });
  };

  const analyzeAllData = () => {
    if (!currentData) {
      toast({
        title: "No Data",
        description: "No data to analyze. Fetch test vectors first.",
        variant: "destructive",
      });
      return;
    }

    const vectors = Array.isArray(currentData) ? currentData : currentData.test_vectors || [];
    
    const analysis = {
      summary: {
        total_vectors: vectors.length,
        timestamp: new Date().toISOString(),
      },
      vectors: vectors.map((v: any) => {
        const actions = v.actions || [];
        const actionCounts = {
          VALID: actions.filter((a: string) => a === "VALID").length,
          DECOY: actions.filter((a: string) => a === "DECOY").length,
          MUTATE: actions.filter((a: string) => a === "MUTATE").length,
          REASSIGN: actions.filter((a: string) => a === "REASSIGN").length,
        };
        
        return {
          id: v.id,
          params: v.params,
          sr: Number.isFinite(v.sr) ? v.sr : null,
          c: Number.isFinite(v.c) ? v.c : null,
          total_leaves: actions.length,
          action_distribution: actionCounts,
          change_percentage:
            actions.length > 0
              ? ((actions.length - actionCounts.VALID) / actions.length * 100).toFixed(2) + "%"
              : "0.00%",
          merkle_root: v.merkle_root_hex,
        };
      }),
    };

    setAnalysisData(analysis);
    setActiveSection("data");
    toast({ title: "Analysis Complete", description: "View results in Data View section" });
  };

  const clearDisplay = () => {
    setCurrentData(null);
    setJavaData(null);
    setPythonData(null);
    setAnalysisData(null);
    setMetrics({
      vectors: "--",
      leaves: "--",
      changed: "--",
      avgSR: "--",
    });
    toast({ title: "Display Cleared", description: "All data has been cleared" });
  };

  const startDocker = () => {
    toast({
      title: "Manual Action Required",
      description: "Open PowerShell and run: docker-compose up -d",
    });
  };

  const stopDocker = () => {
    toast({
      title: "Manual Action Required",
      description: "Open PowerShell and run: docker-compose down",
    });
  };

  const renderContent = () => {
    switch (activeSection) {
      case "dashboard":
        return (
          <div className="space-y-6">
            {/* Header */}
            <div className="bg-card p-6 rounded-lg border border-border/50 shadow-sm">
              <h1 className="text-2xl font-bold text-foreground">Dashboard Overview</h1>
              <p className="text-muted-foreground mt-1">
                Comprehensive monitoring for SQUID Backend & AI Services
              </p>
            </div>

            {/* Service Status */}
            <div>
              <h2 className="text-lg font-semibold mb-3 text-foreground">Service Status</h2>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <ServiceCard
                  name="Java Backend (Port 8080)"
                  detail="API endpoints and test vectors"
                  status={serviceStatus.java}
                  lastCheck="Just now"
                />
                <ServiceCard
                  name="Python AI Service (Port 5000)"
                  detail={
                    pythonInfo
                      ? pythonInfo.model_loaded
                        ? `Model: Loaded (${pythonInfo.model_loaded_path || pythonInfo.model_hash || 'unknown'})`
                        : 'Model: Not loaded'
                      : 'Model: Unknown'
                  }
                  status={serviceStatus.python}
                  lastCheck="Just now"
                />
                <ServiceCard
                  name="Docker Services"
                  detail="Services are running"
                  status={serviceStatus.docker}
                  lastCheck="Just now"
                />
              </div>
            </div>

            {/* Control Panel */}
            <ControlPanel
              onCheckServices={checkServices}
              onStartDocker={startDocker}
              onStopDocker={stopDocker}
              onAnalyze={analyzeAllData}
              onClear={clearDisplay}
              isLoading={isLoading}
            />

            {/* Metrics */}
            <div>
              <h2 className="text-lg font-semibold mb-3 text-foreground">Key Metrics</h2>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <MetricCard label="TEST VECTORS" value={metrics.vectors} subtitle="Total loaded" />
                <MetricCard label="TOTAL LEAVES" value={metrics.leaves} subtitle="Across all vectors" />
                <MetricCard label="CHANGED LEAVES" value={metrics.changed} subtitle="Modified by AI" />
                <MetricCard label="AVG SR VALUE" value={metrics.avgSR} subtitle="Super-Relation" />
              </div>
            </div>
          </div>
        );

      case "merkle":
        return (
          <div className="bg-card p-6 rounded-lg border border-border/50 shadow-sm">
            <h1 className="text-2xl font-bold mb-6 flex items-center gap-2 text-foreground">
              <span>🌳</span> Merkle Tree Visualization
            </h1>
            <EnhancedMerkleTree data={currentData} />
          </div>
        );

      case "ai-decisions":
        return (
          <div>
            <h1 className="text-2xl font-bold mb-6 text-foreground">AI Decisions</h1>
            <AIDecisions data={currentData} />
          </div>
        );

      case "changes":
        return (
          <div>
            <h1 className="text-2xl font-bold mb-6 text-foreground">Changed Leaves</h1>
            <ChangedLeaves data={currentData} />
          </div>
        );

      case "calculations":
        return (
          <div>
            <h1 className="text-2xl font-bold mb-6 text-foreground">SR & C Calculations</h1>
            <SRCalculations data={currentData} />
          </div>
        );

      case "generate":
        return (
          <div>
            <h1 className="text-2xl font-bold mb-6 text-foreground">🔐 Generate Real Data with PQC</h1>
            <RealDataGenerator
              onDataGenerated={(data) => {
                setRealGeneratedData(data);
                const normalized = normalizeData(data);
                setCurrentData([normalized]);
                updateMetrics([normalized]);
              }}
              isLoading={isLoading}
            />
            {realGeneratedData && (
              <div className="mt-6">
                <h2 className="text-lg font-semibold mb-4">Generated Vector Details</h2>
                <DataView
                  rawData={[normalizeData(realGeneratedData)]}
                  javaData={null}
                  pythonData={null}
                  analysisData={null}
                />
              </div>
            )}
          </div>
        );

      case "data":
        return (
          <div>
            <h1 className="text-2xl font-bold mb-6 text-foreground">Detailed Data View</h1>
            <DataView
              rawData={currentData}
              javaData={javaData}
              pythonData={pythonData}
              analysisData={analysisData}
            />
          </div>
        );

      case "settings":
        return (
          <div className="bg-card p-6 rounded-lg border border-border/50 shadow-sm">
            <h1 className="text-2xl font-bold mb-6 text-foreground">Settings</h1>
            <div className="space-y-4">
              <div>
                <h3 className="text-sm font-medium text-foreground mb-2">API Endpoints</h3>
                <div className="space-y-2 text-sm text-muted-foreground">
                  <div>Java Backend: http://localhost:8080</div>
                  <div>Python AI Service: http://localhost:5000</div>
                </div>
              </div>
              <div className="pt-4 border-t border-border/50">
                <h3 className="text-sm font-medium text-foreground mb-2">About</h3>
                <p className="text-sm text-muted-foreground">
                  SQUID Enhanced Dashboard v1.0 - Comprehensive monitoring for Backend & AI Services
                </p>
              </div>
            </div>
          </div>
        );

      case "models":
        return (
          <div>
            <h1 className="text-2xl font-bold mb-6 text-foreground flex items-center gap-2">
              <span>🤖</span> Model Management
            </h1>
            <ModelManager apiBaseUrl="http://localhost:8080/api/v1" />
          </div>
        );

      case "encrypted":
        return (
          <div>
            <h1 className="text-2xl font-bold mb-6 text-foreground flex items-center gap-2">
              <span>🔐</span> Encrypted Data Viewer
            </h1>
            <EncryptedDataViewer apiBaseUrl="http://localhost:8080/api/v1" />
          </div>
        );

      case "merkle-dynamic":
        return (
          <div>
            <h1 className="text-2xl font-bold mb-6 text-foreground flex items-center gap-2">
              <span>🌳</span> Dynamic Merkle Tree
            </h1>
            <DynamicMerkleTreeViewer apiBaseUrl="http://localhost:8080/api/v1" />
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="flex min-h-screen bg-gradient-to-br from-primary/5 via-background to-accent/5">
      <Sidebar activeSection={activeSection} onSectionChange={setActiveSection} />
      
      <main className="flex-1 p-8 overflow-auto">
        <div className="max-w-7xl mx-auto">
          {renderContent()}
        </div>
      </main>
    </div>
  );
};

export default Index;
