export default {
  async fetch(request, env) {
    // Health check endpoint (for easy deployment verification)
    if (request.method === "GET") {
      return new Response(
        JSON.stringify({
          status: "ok",
          service: "sram-oauth-proxy",
          hasSecret: Boolean(env.SRAM_CLIENT_SECRET),
          hasClientId: Boolean(env.SRAM_CLIENT_ID),
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      );
    }

    if (request.method !== "POST") {
      return new Response(JSON.stringify({ error: "method_not_allowed" }), {
        status: 405,
        headers: { "Content-Type": "application/json" },
      });
    }

    try {
      const incomingBody = await request.text();
      const params = new URLSearchParams(incomingBody);

      // Read from Cloudflare environment variable, or fallback to the client_id sent by the app
      const clientId = (env.SRAM_CLIENT_ID || params.get("client_id"))?.trim();
      const clientSecret = env.SRAM_CLIENT_SECRET?.trim();

      if (!clientId) {
        return new Response(
          JSON.stringify({
            error: "server_misconfigured",
            message: "SRAM_CLIENT_ID is missing in Cloudflare Worker environment and request body",
          }),
          {
            status: 500,
            headers: { "Content-Type": "application/json" },
          },
        );
      }

      if (!clientSecret) {
        return new Response(
          JSON.stringify({
            error: "server_misconfigured",
            message: "SRAM_CLIENT_SECRET is missing in Cloudflare Worker environment variables",
          }),
          {
            status: 500,
            headers: { "Content-Type": "application/json" },
          },
        );
      }

      params.set("client_id", clientId);
      params.set("client_secret", clientSecret);

      // Hammerhead's OAuth 2.0 server uses standard client_secret form post and does not use PKCE
      params.delete("code_verifier");

      const response = await fetch("https://api.hammerhead.io/v1/auth/oauth/token", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "Accept": "application/json",
        },
        body: params.toString(),
      });

      const responseText = await response.text();

      return new Response(responseText, {
        status: response.status,
        headers: {
          "Content-Type": response.headers.get("Content-Type") || "application/json",
        },
      });
    } catch (err) {
      return new Response(
        JSON.stringify({ error: "proxy_error", message: err.message }),
        {
          status: 502,
          headers: { "Content-Type": "application/json" },
        },
      );
    }
  },
};
