package moe.shizuku.manager.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import rikka.material.app.MaterialActivity
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R

class LoginActivity : MaterialActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatusMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (MonkeyAuthManager.isLoggedIn(this)) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        progressBar = findViewById(R.id.progress_bar)
        tvStatusMessage = findViewById(R.id.tv_status_message)

        btnLogin.setOnClickListener {
            attemptLogin()
        }
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        hideKeyboard()
        tvStatusMessage.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false

        MonkeyAuthManager.login(this, email, password) { success, message ->
            runOnUiThread {
                progressBar.visibility = View.GONE
                btnLogin.isEnabled = true

                if (success) {
                    startMainActivity()
                } else {
                    tvStatusMessage.text = message
                    tvStatusMessage.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
