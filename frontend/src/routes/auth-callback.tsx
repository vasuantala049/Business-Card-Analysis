import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";

import { useAuth } from "../components/AuthContext";

export const Route = createFileRoute("/auth-callback")({
  component: AuthCallbackPage,
});

function AuthCallbackPage() {
  const navigate = useNavigate();
  const { refetchUser, isAuthenticated } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const finishLogin = async () => {
      try {
        await refetchUser();
        if (!cancelled) {
          navigate({ to: "/" });
        }
      } catch {
        if (!cancelled) {
          setError("Google sign-in completed, but the app couldn't load your session. Please try again.");
        }
      }
    };

    if (isAuthenticated) {
      void navigate({ to: "/" });
      return () => {
        cancelled = true;
      };
    }

    void finishLogin();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, navigate, refetchUser]);

  return (
    <div className="min-h-[80vh] flex items-center justify-center px-4">
      <div className="w-full max-w-md rounded-2xl border border-border bg-card p-8 text-center shadow-xl">
        {!error ? (
          <>
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
            <h1 className="text-2xl font-semibold text-foreground">Signing you in…</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              We just got your Google account. Finishing the handoff to Cardfile.
            </p>
          </>
        ) : (
          <>
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-destructive/10 text-destructive">
              <AlertCircle className="h-6 w-6" />
            </div>
            <h1 className="text-2xl font-semibold text-foreground">Login needs one more try</h1>
            <p className="mt-2 text-sm text-muted-foreground">{error}</p>
            <button
              type="button"
              onClick={() => navigate({ to: "/login" })}
              className="mt-6 inline-flex items-center justify-center gap-2 rounded-xl bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary/90"
            >
              <CheckCircle2 className="h-4 w-4" />
              Back to login
            </button>
          </>
        )}
      </div>
    </div>
  );
}