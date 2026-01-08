import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

interface EncryptedData {
  encryptedDataId: string;
  dataHash: string;
  encapsulatedKey: string;
  ciphertext: string;
  signature: string;
  algorithm: string;
  timestamp: string;
}

interface PreviewSession {
  sessionId: string;
  decryptedData: string;
  dataHash: string;
  isIntegrityValid: boolean;
  signatureValid: boolean;
  expiresAt: string;
  signature: string;
}

interface AuditEntry {
  action: string;
  dataHash: string;
  userId: string;
  ipAddress: string;
  details: string;
  timestamp: string;
}

export function EncryptedDataViewer({ apiBaseUrl = "http://localhost:8080/api/v1" }) {
  const [encryptedDataList, setEncryptedDataList] = useState<EncryptedData[]>([]);
  const [previews, setPreviews] = useState<Map<string, PreviewSession>>(new Map());
  const [auditLog, setAuditLog] = useState<AuditEntry[]>([]);

  const [rawData, setRawData] = useState("");
  const [selectedDataId, setSelectedDataId] = useState<string | null>(null);
  const [userId, setUserId] = useState("user123");
  const [ipAddress, setIpAddress] = useState("127.0.0.1");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Encrypt data with Kyber + Dilithium
  const handleEncrypt = async () => {
    if (!rawData.trim()) {
      setError("Please enter data to encrypt");
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);

    try {
      const response = await fetch(`${apiBaseUrl}/encrypted/encrypt`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-User": userId,
          "X-IP": ipAddress
        },
        body: JSON.stringify({
          data: rawData
        })
      });

      if (response.ok) {
        const encrypted = await response.json();
        setEncryptedDataList([...encryptedDataList, encrypted]);
        setSuccess("Data encrypted successfully!");
        setRawData("");
      } else {
        setError("Failed to encrypt data");
      }
    } catch (err) {
      setError("Error: " + (err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  // Preview encrypted data (secure, temporary)
  const handlePreview = async (dataId: string) => {
    setLoading(true);
    setError(null);

    try {
      const data = encryptedDataList.find((d) => d.encryptedDataId === dataId);
      if (!data) {
        setError("Data not found");
        return;
      }

      const response = await fetch(`${apiBaseUrl}/encrypted/preview`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-User": userId,
          "X-IP": ipAddress
        },
        body: JSON.stringify({
          encryptedData: data
        })
      });

      if (response.ok) {
        const preview = await response.json();
        previews.set(dataId, preview);
        setPreviews(new Map(previews));
        setSelectedDataId(dataId);
        
        setSuccess(`Preview created! Expires in 5 minutes.`);
        setTimeout(() => setSuccess(null), 3000);
      } else {
        const err = await response.json();
        setError("Preview failed: " + (err.message || "Unknown error"));
      }
    } catch (err) {
      setError("Error: " + (err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  // Fetch audit log
  const handleFetchAuditLog = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`${apiBaseUrl}/encrypted/audit`, {
        headers: {
          "X-User": userId,
          "X-IP": ipAddress
        }
      });

      if (response.ok) {
        const data = await response.json();
        setAuditLog(data.entries || data);
      } else {
        setError("Failed to fetch audit log");
      }
    } catch (err) {
      setError("Error: " + (err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const selectedPreview = selectedDataId ? previews.get(selectedDataId) : null;
  const timeToExpire = selectedPreview
    ? Math.max(
        0,
        Math.floor(
          (new Date(selectedPreview.expiresAt).getTime() - Date.now()) / 1000
        )
      )
    : 0;

  return (
    <div className="space-y-6">
      {/* Error Alert */}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Success Alert */}
      {success && (
        <Alert className="border-green-300 bg-green-50">
          <AlertDescription className="text-green-800">{success}</AlertDescription>
        </Alert>
      )}

      {/* User Context */}
      <Card className="border-blue-200 bg-blue-50">
        <CardHeader>
          <CardTitle className="text-blue-600">Session Context</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div>
            <label className="block text-sm font-semibold mb-1">User ID</label>
            <Input
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="user123"
              className="bg-white"
            />
          </div>
          <div>
            <label className="block text-sm font-semibold mb-1">IP Address</label>
            <Input
              value={ipAddress}
              onChange={(e) => setIpAddress(e.target.value)}
              placeholder="127.0.0.1"
              className="bg-white"
            />
          </div>
        </CardContent>
      </Card>

      <Tabs defaultValue="encrypt" className="w-full">
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="encrypt">Encrypt</TabsTrigger>
          <TabsTrigger value="preview">Preview</TabsTrigger>
          <TabsTrigger value="audit">Audit</TabsTrigger>
          <TabsTrigger value="info">Info</TabsTrigger>
        </TabsList>

        {/* Encrypt Tab */}
        <TabsContent value="encrypt">
          <Card>
            <CardHeader>
              <CardTitle>Encrypt Data with Kyber + Dilithium</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <label className="block text-sm font-semibold mb-2">Raw Data</label>
                <Textarea
                  value={rawData}
                  onChange={(e) => setRawData(e.target.value)}
                  placeholder="Enter sensitive data to encrypt..."
                  rows={6}
                  className="font-mono text-sm"
                />
              </div>

              <Button
                onClick={handleEncrypt}
                disabled={loading || !rawData.trim()}
                className="bg-blue-600 hover:bg-blue-700 w-full"
              >
                {loading ? "Encrypting..." : "Encrypt Data"}
              </Button>

              {/* Encrypted Data List */}
              {encryptedDataList.length > 0 && (
                <div className="mt-6 space-y-2">
                  <h3 className="font-semibold text-sm">Encrypted Data Items</h3>
                  <div className="space-y-2 max-h-48 overflow-y-auto">
                    {encryptedDataList.map((data) => (
                      <div
                        key={data.encryptedDataId}
                        className="p-3 rounded border bg-gray-50 hover:bg-gray-100 cursor-pointer transition"
                        onClick={() => handlePreview(data.encryptedDataId)}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex-1 min-w-0">
                            <div className="font-mono text-xs text-gray-600 truncate">
                              {data.encryptedDataId}
                            </div>
                            <div className="text-xs text-gray-500 mt-1">
                              Hash: {data.dataHash.slice(0, 16)}...
                            </div>
                          </div>
                          <Badge variant="outline" className="ml-2">
                            {data.algorithm}
                          </Badge>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Preview Tab */}
        <TabsContent value="preview">
          <Card>
            <CardHeader>
              <CardTitle>Secure Preview (Temporary, Not Persisted)</CardTitle>
              <p className="text-sm text-gray-600 mt-2">
                ⚠️ Previews expire in 5 minutes and are never stored
              </p>
            </CardHeader>
            <CardContent className="space-y-4">
              {selectedPreview ? (
                <div className="space-y-4">
                  {/* Session Info */}
                  <div className="grid grid-cols-2 gap-4 p-3 bg-blue-50 rounded">
                    <div>
                      <span className="text-xs font-semibold text-blue-600">Session ID</span>
                      <div className="font-mono text-xs text-gray-700 mt-1">
                        {selectedPreview.sessionId.slice(0, 12)}...
                      </div>
                    </div>
                    <div>
                      <span className="text-xs font-semibold text-blue-600">Time to Expire</span>
                      <div className="text-sm font-bold text-blue-700 mt-1">
                        {timeToExpire}s
                      </div>
                    </div>
                  </div>

                  {/* Integrity Status */}
                  <div className="grid grid-cols-2 gap-4">
                    <div className="p-3 rounded border-2 border-green-300 bg-green-50">
                      <div className="text-xs font-semibold text-green-600">Data Integrity</div>
                      <Badge className="mt-2 bg-green-600">
                        {selectedPreview.isIntegrityValid ? "✓ Valid" : "✗ Compromised"}
                      </Badge>
                    </div>
                    <div className="p-3 rounded border-2 border-green-300 bg-green-50">
                      <div className="text-xs font-semibold text-green-600">Signature</div>
                      <Badge className="mt-2 bg-green-600">
                        {selectedPreview.signatureValid ? "✓ Verified" : "✗ Invalid"}
                      </Badge>
                    </div>
                  </div>

                  {/* Decrypted Data */}
                  <div>
                    <label className="block text-sm font-semibold mb-2">Decrypted Data</label>
                    <div className="p-4 rounded bg-gray-100 font-mono text-sm whitespace-pre-wrap break-words border-2 border-green-300 max-h-40 overflow-y-auto">
                      {selectedPreview.decryptedData}
                    </div>
                  </div>

                  {/* Data Hash */}
                  <div>
                    <label className="block text-xs font-semibold mb-2 text-gray-600">
                      SHA-256 Hash
                    </label>
                    <div className="font-mono text-xs bg-gray-100 p-2 rounded break-all">
                      {selectedPreview.dataHash}
                    </div>
                  </div>

                  {/* Signature Verification */}
                  <div>
                    <label className="block text-xs font-semibold mb-2 text-gray-600">
                      Dilithium Signature
                    </label>
                    <div className="font-mono text-xs bg-gray-100 p-2 rounded break-all max-h-24 overflow-y-auto">
                      {selectedPreview.signature}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="text-center py-8 text-gray-500">
                  <p>Select an encrypted item from the Encrypt tab to preview</p>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Audit Tab */}
        <TabsContent value="audit">
          <Card>
            <CardHeader>
              <CardTitle>Audit Log</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Button
                onClick={handleFetchAuditLog}
                disabled={loading}
                className="bg-blue-600 hover:bg-blue-700"
              >
                {loading ? "Loading..." : "Refresh Audit Log"}
              </Button>

              {auditLog.length > 0 && (
                <div className="space-y-2 max-h-96 overflow-y-auto">
                  {auditLog.map((entry, idx) => (
                    <div key={idx} className="p-3 rounded border bg-gray-50">
                      <div className="flex items-center justify-between mb-2">
                        <Badge
                          variant="outline"
                          className={
                            entry.action.includes("SUCCESS")
                              ? "bg-green-100 text-green-800"
                              : entry.action.includes("FAILED")
                              ? "bg-red-100 text-red-800"
                              : "bg-blue-100 text-blue-800"
                          }
                        >
                          {entry.action}
                        </Badge>
                        <span className="text-xs text-gray-500">
                          {new Date(entry.timestamp).toLocaleTimeString()}
                        </span>
                      </div>
                      <div className="text-xs text-gray-600 space-y-1">
                        <div>User: {entry.userId}</div>
                        <div>IP: {entry.ipAddress}</div>
                        <div className="font-mono text-xs mt-2 p-2 bg-white rounded">
                          Hash: {entry.dataHash.slice(0, 20)}...
                        </div>
                        {entry.details && (
                          <div className="text-gray-700 mt-2">{entry.details}</div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Info Tab */}
        <TabsContent value="info">
          <Card>
            <CardHeader>
              <CardTitle>Encryption Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4 text-sm">
              <div className="p-4 bg-blue-50 rounded">
                <h3 className="font-semibold text-blue-600 mb-2">Kyber (ML-KEM)</h3>
                <p className="text-gray-700">
                  Post-quantum key encapsulation mechanism for secure key derivation.
                  Encrypts data with a derived symmetric key.
                </p>
              </div>

              <div className="p-4 bg-green-50 rounded">
                <h3 className="font-semibold text-green-600 mb-2">Dilithium (ML-DSA)</h3>
                <p className="text-gray-700">
                  Post-quantum digital signature algorithm. Creates cryptographic signatures
                  that verify data authenticity and integrity.
                </p>
              </div>

              <div className="p-4 bg-yellow-50 rounded">
                <h3 className="font-semibold text-yellow-600 mb-2">Session Security</h3>
                <ul className="text-gray-700 space-y-1 list-disc list-inside">
                  <li>Sessions expire after 5 minutes</li>
                  <li>Decrypted data is never persisted</li>
                  <li>All operations are audited</li>
                  <li>User and IP context tracked</li>
                </ul>
              </div>

              <div className="p-4 bg-purple-50 rounded">
                <h3 className="font-semibold text-purple-600 mb-2">Integrity Verification</h3>
                <ul className="text-gray-700 space-y-1 list-disc list-inside">
                  <li>SHA-256 hash verification</li>
                  <li>Dilithium signature validation</li>
                  <li>Real-time integrity checks</li>
                </ul>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
