package id.daewu14.google_one_tap_sign_in

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.NonNull
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.ApiException
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry


/**
 * GoogleOneTapSignInPlugin
 *
 * @author
 *
 * Daewu Bintara
 * (daewu.bintara1996@gmail.com) | Indonesia
 *
 * Tuesday, 04/01/22 18:29
 *
 * Enjoy coding ☕
 *
 */
class GoogleOneTapSignInPlugin : FlutterPlugin, MethodCallHandler, MethodContract,
    PluginRegistry.ActivityResultListener, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var pluginBinding: FlutterPluginBinding

    private var activity: Activity? = null
    private var context: Context? = null
    private var webCLientId: String? = null
    private var enableGoogleAccount: Boolean? = null
    private var result: MethodChannel.Result? = null

    /**
     * Get binding to controll all the activity of Flutter App from Flutter Engine
     */
    private var binding: ActivityPluginBinding? = null


    private var DAEWU: String = "---DAEWU14---"
    private var channelName: String = "google_one_tap_sign_in"

    /**
     * Unique [REQUEST_CODE]
     */
    private val REQ_ONE_TAP = 14081996
    private val REQ_SAVE_PASS = 14081997

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        setupPlugin(null, flutterPluginBinding)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        this.result = result
        when (call.method) {
            "getPlatformVersion" -> {
                finishWithResult("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "startSignIn" -> {
                webCLientId = call.argument("web_client_id")
                enableGoogleAccount = call.argument("enable_google_account")
                startSignIn()
            }
            "savePassword" -> {
                var username: String? = call.argument("username")
                var password: String? = call.argument("password")
                savePassword(username, password)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        detachPlugin()
        channel.setMethodCallHandler(null)
    }

    override fun savePassword(userId: String?, password: String?) {
        if (userId == null || password == null) {
            finishWithResult(false)
            return
        }
        val signInPassword = SignInPassword(userId!!, password!!)
        val savePasswordRequest =
            SavePasswordRequest.builder().setSignInPassword(signInPassword).build()
        Identity.getCredentialSavingClient(activity)
            .savePassword(savePasswordRequest)
            .addOnSuccessListener { result ->
                activity!!.startIntentSenderForResult(
                    result.pendingIntent.intentSender,
                    REQ_SAVE_PASS,
                    /* fillInIntent= */ null,
                    /* flagsMask= */ 0,
                    /* flagsValue= */ 0,
                    /* extraFlags= */ 0,
                    /* options= */ null
                )
            }.addOnFailureListener { e ->
                finishWithResult(false)
            }
            .addOnCanceledListener {
                finishWithResult(false)
            }
    }

    override fun startSignIn() {
        if (webCLientId == null) {
            return
        }

        if (result == null) {
            return
        }

        if (activity == null) {
            return
        }

        oneTapClient = Identity.getSignInClient(activity)

        oneTapClient.signOut()

        signInRequest = BeginSignInRequest.builder()
            .setPasswordRequestOptions(
                BeginSignInRequest.PasswordRequestOptions.builder()
                    .setSupported(true)
                    .build()
            )
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(if (enableGoogleAccount == null) false else enableGoogleAccount!!)
                    // Your server's client ID, not your Android client ID.
                    .setServerClientId(webCLientId)
                    // Only show accounts previously used to sign in.
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            // Automatically sign in when exactly one credential is retrieved.
//      .setAutoSelectEnabled(true)
            .build()

        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener { rss ->
                Log.d("ON LOGIN", "PROCESS")
                activity!!.startIntentSenderForResult(
                    rss.pendingIntent.intentSender, REQ_ONE_TAP,
                    null, 0, 0, 0, null
                )
            }
            .addOnFailureListener { e ->
                e.message?.let {
                    Log.d("Error", it)
                    if (it.contains("Caller has been temporarily blocked due to too many canceled sign-in prompts.")) {
                        finishWithResult("TEMPORARY_BLOCKED")
                    } else {
                        finishWithResult(null)
                    }
                }
                if (e.message == null) {
                    finishWithResult(null)
                }
            }
            .addOnCanceledListener {
                finishWithResult("CANCELED")
            }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (binding == null) {
            return false
        }
        Log.d(DAEWU, "Req Code : $requestCode")
        result?.let {
            when (requestCode) {
                REQ_ONE_TAP -> {
                    if (data != null) {
                        try {
                            val credential = oneTapClient.getSignInCredentialFromIntent(data)
                            val idToken = credential.googleIdToken
                            val username = credential.id
                            val password = credential.password
                            val displayName = credential.displayName
                            Log.d(DAEWU, "~~~~ ☕ ONE TAP SUCCESS ☕ ~~~~")
                            val params: MutableMap<String, Any?> = mutableMapOf()
                            params["id_token"] = idToken
                            params["username"] = username
                            params["password"] = password
                            params["display_name"] = displayName

                            finishWithResult(params)
                            return true
                        } catch (e: ApiException) {
                            Log.d(DAEWU, "~~~~ !! ONE TAP ApiException !! ~~~~")
                            finishWithResult(null)
                            return false
                        }
                    } else {
                        Log.d(DAEWU, "~~~~ !! ONE TAP Data Null !! ~~~~")
                        finishWithResult(null)
                        return true
                    }
                }
                REQ_SAVE_PASS -> {
                    if (resultCode == Activity.RESULT_OK) {
                        Log.d(DAEWU, "~~~~ !! ONE TAP save pass success !! ~~~~")
                        finishWithResult(true)
                        return true
                    } else {
                        Log.d(DAEWU, "~~~~ !! ONE TAP save pass fail !! ~~~~")
                        finishWithResult(false)
                        return false
                    }
                }
                else -> {
                    return false
                }
            }
        }
        Log.d(DAEWU, "~~~~ !! ONE TAP Result Unknown !! ~~~~")
        return false
    }

    private fun finishWithResult(data: Any?) {
        if (result != null) {
            result!!.success(data)
            result = null
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        setupPlugin(binding, null)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        detachPlugin()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {}

    /**
     * To Detach this Plugin
     */
    private fun detachPlugin() {
        if (binding == null) {
            return
        }
        this.binding!!.removeActivityResultListener(this)
        this.binding = null
    }

    /**
     * Setup this Plugin
     */
    private fun setupPlugin(
        binding: ActivityPluginBinding?,
        flutterPluginBinding: FlutterPlugin.FlutterPluginBinding?
    ) {

        // let Binding the FlutterPluginBinding
        flutterPluginBinding?.let {
            pluginBinding = it
        }

        // Let Plugin Binding
        pluginBinding.let {
            context = it.applicationContext
            channel = MethodChannel(it.binaryMessenger, channelName)
            channel.setMethodCallHandler(this)
        }

        // Let Binding the ActivityPluginBinding
        binding?.let {
            activity = it.activity
            this.binding = it
            this.binding!!.addActivityResultListener(this)
        }
    }

}
