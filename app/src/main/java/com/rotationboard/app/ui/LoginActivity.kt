package com.rotationboard.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rotationboard.app.data.AppDatabase
import com.rotationboard.app.data.UserEntity
import com.rotationboard.app.databinding.ActivityLoginBinding
import com.rotationboard.app.util.PasswordUtils
import com.rotationboard.app.util.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (SessionManager.isLoggedIn(this)) {
            goToDashboard()
            return
        }

        updateModeUi()

        binding.tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateModeUi()
        }

        binding.btnSubmit.setOnClickListener {
            if (isLoginMode) doLogin() else doRegister()
        }
    }

    private fun updateModeUi() {
        binding.tvTitle.text = if (isLoginMode) "Log in" else "Register"
        binding.btnSubmit.text = if (isLoginMode) "Log in" else "Create account"
        binding.tilConfirmPassword.visibility =
            if (isLoginMode) android.view.View.GONE else android.view.View.VISIBLE
        binding.tvToggleMode.text =
            if (isLoginMode) "New here? Register instead" else "Already registered? Log in instead"
        binding.tvError.text = ""
    }

    private fun doLogin() {
        val username = binding.etUsername.text.toString().trim().lowercase()
        val password = binding.etPassword.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            binding.tvError.text = "Enter both username and password."
            return
        }
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val user = db.userDao().findByUsername(username)
            if (user == null) {
                binding.tvError.text = "No account with that username. Register first."
                return@launch
            }
            if (PasswordUtils.hash(password) != user.passwordHash) {
                binding.tvError.text = "Incorrect password."
                return@launch
            }
            SessionManager.saveSession(applicationContext, user.id, user.username)
            goToDashboard()
        }
    }

    private fun doRegister() {
        val username = binding.etUsername.text.toString().trim().lowercase()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirmPassword.text.toString()

        if (username.length < 3) {
            binding.tvError.text = "Username must be at least 3 characters."
            return
        }
        if (password.length < 6) {
            binding.tvError.text = "Password must be at least 6 characters."
            return
        }
        if (password != confirm) {
            binding.tvError.text = "Passwords don't match."
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val existing = db.userDao().findByUsername(username)
            if (existing != null) {
                binding.tvError.text = "That username is already taken."
                return@launch
            }
            val newUser = UserEntity(username = username, passwordHash = PasswordUtils.hash(password))
            val id = db.userDao().insert(newUser)
            SessionManager.saveSession(applicationContext, id, username)
            goToDashboard()
        }
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
