package com.example.filman.data.server

import fi.iki.elonen.NanoHTTPD

class CredentialServer(
    port: Int,
    private val onCookieReceived: (String) -> Unit,
    private val onCredentialsReceived: (String, String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            val files = mutableMapOf<String, String>()
            try {
                session.parseBody(files)
                val params = session.parameters

                if (params.containsKey("cookie")) {
                    val cookie = params["cookie"]?.get(0)
                    if (!cookie.isNullOrEmpty()) {
                        onCookieReceived(cookie)
                        return newFixedLengthResponse("Cookie received! You can look at the TV now.")
                    }
                } else if (params.containsKey("username") && params.containsKey("password")) {
                    val username = params["username"]?.get(0)
                    val password = params["password"]?.get(0)
                    if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                        onCredentialsReceived(username, password)
                        return newFixedLengthResponse("Credentials sent! Complete the reCAPTCHA on the TV.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Filman.cc TV Setup</title>
                <style>
                    body { font-family: sans-serif; padding: 20px; background: #f0f0f0; }
                    .card { background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    input { width: 100%; padding: 10px; margin: 10px 0; box-sizing: border-box; border: 1px solid #ccc; border-radius: 4px; }
                    button { width: 100%; padding: 12px; background: #007bff; color: white; border: none; border-radius: 4px; font-size: 16px; }
                </style>
            </head>
            <body>
                <h2>Filman.cc TV Setup</h2>
                
                <div class="card">
                    <h3>Option 1: Paste Cookie (Recommended)</h3>
                    <p>Log in to filman.cc on this device, copy the cookie (e.g. PHPSESSID=...) and paste it below:</p>
                    <form method="POST">
                        <input type="text" name="cookie" placeholder="Paste cookie here..." required>
                        <button type="submit">Send Cookie</button>
                    </form>
                </div>

                <div class="card">
                    <h3>Option 2: Send Credentials (Fallback)</h3>
                    <p>Send your username and password to the TV to autofill the login form.</p>
                    <form method="POST">
                        <input type="text" name="username" placeholder="Username" required>
                        <input type="password" name="password" placeholder="Password" required>
                        <button type="submit">Send Credentials</button>
                    </form>
                </div>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(html)
    }
}
