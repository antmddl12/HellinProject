package com.example.hellinproject

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.android.synthetic.main.activity_login.*

import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.util.*


class LoginActivity : AppCompatActivity() {
    // firebase 인증을 위한 변수
    var auth : FirebaseAuth? = null
    // 구글 로그인 연동에 필요한 변수
    var googleSignInClient : GoogleSignInClient? = null
    var GOOGLE_LOGIN_CODE = 9001
    // 페이스북 로그인 결과를 가져오는 callbackManager
    var callbackManager : CallbackManager? = null
    var database : FirebaseDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        // 초기화
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        // 구글 로그인 버튼
        google_sign_in_button.setOnClickListener {
            // First step
            googleLogin()
        }
        // 페이스북 로그인 버튼
        facebook_login_button.setOnClickListener {
            // First step
            facebookLogin()
        }

        // 사용자 ID와 프로필 정보를 요청하기 위해 gso를 인자로 전달해서 GoogleSignInClient 객체 생성
        var gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//            .requestIdToken(getString(R.string.default_web_client_id))
            .requestIdToken("353497376762-p5sgflf136jnhf2cu39bp4ajjri0o8vl.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 로그인 응답을 처리할 CallbackManager
        callbackManager = CallbackManager.Factory.create()

//        printHashKey()
    }

    // 자동 로그인
    override fun onStart() {
        super.onStart()
        moveMainPage(auth?.currentUser)
    }

    // googleSignInClient를 signInIntent 메소드를 통해서 signInIntent를 만들고, startActivityForResult에 전달
    // 새 액티비티를 열고, 결과 값 전달 => onActivityResult()가 결과 값 받음
    // 사용자가 signIn에 성공하면 onActivityResult() 실행
    fun googleLogin() {
        var signInIntent = googleSignInClient?.signInIntent
        startActivityForResult(signInIntent, GOOGLE_LOGIN_CODE)
    }

    fun facebookLogin() {
        // 페이스북에서 받을 권한 요청 - 프로필 이미지, 이메일
        LoginManager.getInstance()
            .logInWithReadPermissions(this, Arrays.asList("public_profile", "email"))
        // LoginResult : 페이스북 로그인이 최종 성공했을 때 넘어오는 부분
        LoginManager.getInstance()
            .registerCallback(callbackManager, object  : FacebookCallback<LoginResult>{
                override fun onSuccess(result: LoginResult?) {
                    // Second step
                    // 로그인이 성공하면 페이스북 데이터를 파이어베이스에 넘기는 함수
                    handleFacebookAccessToken(result?.accessToken)
                }

                override fun onCancel() {
                }

                override fun onError(error: FacebookException?) {
                }

            })
    }

    // 페이스북 데이터를 firebase에 넘김
    fun handleFacebookAccessToken(token : AccessToken?) {
        var credential = FacebookAuthProvider.getCredential(token?.token!!)
        auth?.signInWithCredential(credential)
            ?.addOnCompleteListener {
                task ->
                if (task.isSuccessful) {
                    // Third step
                    // Login, 아이디와 패스워드가 맞았을 때
                    moveMainPage(task.result?.user)
                } else {
                    // Show the error message, 아이디와 패스워드가 틀렸을 때
                    Toast.makeText(this, task.exception?.message, Toast.LENGTH_LONG).show()
                }
            }
    }

    // requestCode == 구글 로그인 코드
    // result에 구글에 로그인했을 때 구글에서 넘겨주는 결과 값 저장
    // result가 성공하면 firebaseAuthWithGoogle()에 결과 아이디 넘겨줌
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 로그인 결과를 callbackManager를 통해 loginManager에게 전달
        callbackManager?.onActivityResult(requestCode, resultCode, data)
        if(requestCode == GOOGLE_LOGIN_CODE) {
            var result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            // result가 성공했을 때 이 값을 firebase에 넘겨주기
            if (result!!.isSuccess) {
                var account = result.signInAccount
                // Second step
                firebaseAuthWithGoogle(account)
            }
        }
    }

    // account에서 id 토큰을 가져와서 firebase 사용자 인증 정보로 교환해야 함 => signInWithCredential()
    fun firebaseAuthWithGoogle(account : GoogleSignInAccount?) {
        var credential = GoogleAuthProvider.getCredential(account?.idToken, null)
        auth?.signInWithCredential(credential)
            ?.addOnCompleteListener {
                task ->
                if(task.isSuccessful) {
                    // Login, 아이디와 패스워드가 맞았을 때
                    moveMainPage(task.result?.user)
                } else {
                    // Show the error message, 아이디와 패스워드가 틀렸을 때
                    Toast.makeText(this, task.exception?.message, Toast.LENGTH_LONG).show()
                }
            }
    }

    // 로그인이 성공하면 다음 페이지로 넘어가는 함수
    fun moveMainPage(user : FirebaseUser?) {
        // 파이어베이스 유저 상태가 있을 경우 다음 페이지로 넘어갈 수 있음
        if (user != null) {
            var uid = FirebaseAuth.getInstance().currentUser?.uid
            var databaseRef : DatabaseReference = database!!.reference

            databaseRef.child("users").child(uid!!).addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    // 유저 정보가 없는 경우 초기 프로필 세팅 화면으로 넘어감
                    if (snapshot.child("uid").value == null) {
                        startActivity(Intent(this@LoginActivity, ProfileSettingActivity::class.java))
                    }
                    // 유저 정보가 있는 경우 바로 메인 페이지로 넘어감
                    else {
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                }
            })
        }
    }
}

    /*// 페이스북 로그인을 위한 Hash 값을 얻기 위한 함수
    fun printHashKey() {
        try {
            val info: PackageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md: MessageDigest = MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val hashKey: String = String(Base64.encode(md.digest(), 0))
                Log.i("TAG", "printHashKey() Hash Key: $hashKey")
            }
        } catch (e: NoSuchAlgorithmException) {
            Log.e("TAG", "printHashKey()", e)
        } catch (e: Exception) {
            Log.e("TAG", "printHashKey()", e)
        }
    }*/