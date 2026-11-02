package com.safewalk.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF171A19)
private val Muted = Color(0xFF737A77)
private val Line = Color(0xFFE2E5E3)
private val Soft = Color(0xFFF5F6F5)
private val ScreenBackground = Color(0xFFFAFBFA)
private val Accent = Color(0xFF252A28)
private val ErrorRed = Color(0xFFB3261E)

@Composable
fun LoginScreen(
    state: LoginUiState,
    onLoginEmailChange: (String) -> Unit,
    onLoginPasswordChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onSignUpEmailChange: (String) -> Unit,
    onSignUpPasswordChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onGuardianNameChange: (String) -> Unit,
    onGuardianPhoneChange: (String) -> Unit,
    onTermsAgreementChange: (Boolean) -> Unit,
    onPrivacyAgreementChange: (Boolean) -> Unit,
    onLocationAgreementChange: (Boolean) -> Unit,
    onAllAgreementsChange: (Boolean) -> Unit,
    onUserTypeChange: (UserType) -> Unit,
    onNotificationPermissionChange: (Boolean) -> Unit,
    onActivityPermissionChange: (Boolean) -> Unit,
    onContactsPermissionChange: (Boolean) -> Unit,
    onOpenSignUp: () -> Unit,
    onContinueToTerms: () -> Unit,
    onContinueToUserType: () -> Unit,
    onContinueToPermissions: () -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onStart: () -> Unit,
    onSkipPermissions: () -> Unit,
) {
    BackHandler(enabled = state.step != AuthStep.LOGIN, onBack = onBack)
    MaterialTheme {
        Surface(color = ScreenBackground, modifier = Modifier.fillMaxSize()) {
            when (state.step) {
                AuthStep.LOGIN -> LoginPage(
                    state = state,
                    onEmailChange = onLoginEmailChange,
                    onPasswordChange = onLoginPasswordChange,
                    onLogin = onLogin,
                    onContinueAsGuest = onContinueAsGuest,
                    onOpenSignUp = onOpenSignUp,
                )
                AuthStep.SIGN_UP -> SignUpPage(
                    state = state,
                    onNameChange = onNameChange,
                    onEmailChange = onSignUpEmailChange,
                    onPasswordChange = onSignUpPasswordChange,
                    onPhoneNumberChange = onPhoneNumberChange,
                    onBack = onBack,
                    onNext = onContinueToTerms,
                )
                AuthStep.TERMS -> TermsAgreementPage(
                    state = state,
                    onTermsAgreementChange = onTermsAgreementChange,
                    onPrivacyAgreementChange = onPrivacyAgreementChange,
                    onLocationAgreementChange = onLocationAgreementChange,
                    onAllAgreementsChange = onAllAgreementsChange,
                    onBack = onBack,
                    onNext = onContinueToUserType,
                )
                AuthStep.USER_TYPE -> UserTypePage(
                    state = state,
                    onUserTypeChange = onUserTypeChange,
                    onGuardianNameChange = onGuardianNameChange,
                    onGuardianPhoneChange = onGuardianPhoneChange,
                    onBack = onBack,
                    onNext = onContinueToPermissions,
                )
                AuthStep.PERMISSIONS -> PermissionPage(
                    state = state,
                    onNotificationChange = onNotificationPermissionChange,
                    onActivityChange = onActivityPermissionChange,
                    onContactsChange = onContactsPermissionChange,
                    onBack = onBack,
                    onStart = onStart,
                    onSkip = onSkipPermissions,
                )
            }
        }
    }
}

@Composable
private fun LoginPage(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onOpenSignUp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.75f))
        BrandMark()
        Spacer(Modifier.height(38.dp))
        AppTextField(
            value = state.loginEmail,
            onValueChange = onEmailChange,
            label = "이메일",
            placeholder = "이메일을 입력해주세요",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(14.dp))
        AppTextField(
            value = state.loginPassword,
            onValueChange = onPasswordChange,
            label = "비밀번호",
            placeholder = "비밀번호를 입력해주세요",
            password = true,
            imeAction = ImeAction.Done,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {}) {
                Text("비밀번호 찾기", color = Muted, fontSize = 12.sp)
            }
        }
        PrimaryButton(if (state.isLoading) "로그인 중..." else "로그인", state.canLogin, onLogin)
        state.errorMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = Color(0xFFB3261E),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {},
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("○  카카오로 계속하기", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        TextButton(
            onClick = onContinueAsGuest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("비회원으로 둘러보기", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("처음이신가요?", color = Muted, fontSize = 13.sp)
            TextButton(onClick = onOpenSignUp) {
                Text("회원가입", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(54.dp).background(Color(0xFFE8EAE9), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("♢", color = Color(0xFF6F7773), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text("SafeWalk", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(7.dp))
        Text("더 안전한 길을 찾아 나만의 안심 동행", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SignUpPage(
    state: LoginUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingLayout(title = "회원가입", step = 1, onBack = onBack) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("기본 정보를 입력해 주세요", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text("안전한 동행을 위해 간단한 정보가 필요해요.", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(24.dp))
            AppTextField(state.name, onNameChange, "이름", "이름을 입력해주세요")
            Spacer(Modifier.height(14.dp))
            AppTextField(state.signUpEmail, onEmailChange, "이메일", "example@email.com", KeyboardType.Email)
            Spacer(Modifier.height(14.dp))
            AppTextField(
                state.signUpPassword, onPasswordChange, "비밀번호", "비밀번호를 입력해주세요",
                password = true,
            )
            Spacer(Modifier.height(14.dp))
            AppTextField(
                state.phoneNumber, onPhoneNumberChange, "휴대폰 번호", "010-0000-0000",
                KeyboardType.Phone,
            )
            Spacer(Modifier.height(18.dp))
        }
        PrimaryButton("다음", state.canContinueSignUp, onNext)
    }
}

@Composable
private fun TermsAgreementPage(
    state: LoginUiState,
    onTermsAgreementChange: (Boolean) -> Unit,
    onPrivacyAgreementChange: (Boolean) -> Unit,
    onLocationAgreementChange: (Boolean) -> Unit,
    onAllAgreementsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingLayout(title = "약관 동의", step = 2, onBack = onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("서비스 이용을 위해\n동의가 필요해요", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Spacer(Modifier.height(7.dp))
            Text("필수 항목에 동의해야 회원가입을 계속할 수 있습니다.", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(24.dp))

            AgreementRow(
                title = "전체 동의",
                description = "필수 및 선택 항목을 모두 확인하고 동의합니다.",
                checked = state.allAgreementsSelected,
                emphasized = true,
                onCheckedChange = onAllAgreementsChange,
            )
            Spacer(Modifier.height(12.dp))
            AgreementRow(
                title = "[필수] 서비스 이용약관 동의",
                description = "서비스 이용 조건과 사용자 책임에 관한 내용입니다.",
                checked = state.agreedToTerms,
                onCheckedChange = onTermsAgreementChange,
            )
            AgreementRow(
                title = "[필수] 개인정보 수집·이용 동의",
                description = "이름, 이메일, 휴대폰 번호를 회원 관리와 안전 기능 제공에 사용하며 탈퇴 시 파기합니다.",
                checked = state.agreedToPrivacy,
                onCheckedChange = onPrivacyAgreementChange,
            )
            AgreementRow(
                title = "[선택] 위치기반 서비스 이용 동의",
                description = "현재 위치를 활용해 주변 안전시설과 경로를 안내합니다.",
                checked = state.agreedToLocation,
                onCheckedChange = onLocationAgreementChange,
            )
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton("동의하고 계속", state.canContinueTerms, onNext)
    }
}

@Composable
private fun AgreementRow(
    title: String,
    description: String,
    checked: Boolean,
    emphasized: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (emphasized) Soft else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = if (emphasized) 10.dp else 2.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Accent),
        )
        Column(Modifier.weight(1f).padding(top = 10.dp, end = 4.dp)) {
            Text(title, color = Ink, fontSize = 13.sp, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(description, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun UserTypePage(
    state: LoginUiState,
    onUserTypeChange: (UserType) -> Unit,
    onGuardianNameChange: (String) -> Unit,
    onGuardianPhoneChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingLayout(title = "사용자 유형 선택", step = 3, onBack = onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("어떻게 이용하시나요?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text("알맞은 유형을 선택하면 꼭 필요한 기능을 먼저 보여드려요.", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(24.dp))
            UserTypeCard(
                icon = "♙", title = "아이", description = "안전한 길을 안내받고 보호자와 위치를 공유해요.",
                selected = state.userType == UserType.CHILD,
                onClick = { onUserTypeChange(UserType.CHILD) },
            )
            Spacer(Modifier.height(12.dp))
            UserTypeCard(
                icon = "♡", title = "보호자", description = "아이의 이동 경로와 현재 안전 상태를 확인해요.",
                selected = state.userType == UserType.GUARDIAN,
                onClick = { onUserTypeChange(UserType.GUARDIAN) },
            )
            Spacer(Modifier.height(12.dp))
            UserTypeCard(
                icon = "♧", title = "보행자 · 동행자", description = "더 안전한 길을 찾고 필요한 사람과 동행해요.",
                selected = state.userType == UserType.COMPANION,
                onClick = { onUserTypeChange(UserType.COMPANION) },
            )
            if (state.userType == UserType.CHILD) {
                Spacer(Modifier.height(18.dp))
                Text("대표 보호자", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AppTextField(state.guardianName, onGuardianNameChange, "보호자 이름", "예: 김보호")
                Spacer(Modifier.height(10.dp))
                AppTextField(state.guardianPhone, onGuardianPhoneChange, "보호자 전화번호", "010-0000-0000", KeyboardType.Phone)
                Spacer(Modifier.height(6.dp))
                Text("보호자 계정의 같은 전화번호로 초대를 보내며, 상대방이 수락해야 연결됩니다.", color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
        PrimaryButton("다음", state.canContinueUserType, onNext)
    }
}

@Composable
private fun UserTypeCard(
    icon: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) Ink else Line
    Row(
        modifier = Modifier.fillMaxWidth().border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(9.dp))
            .background(if (selected) Color(0xFFF3F4F3) else Color.White, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(Soft, CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, color = Muted, fontSize = 21.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Box(
            Modifier.size(18.dp).border(1.5.dp, if (selected) Ink else Color(0xFFB7BCB9), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(Ink, CircleShape))
        }
    }
}

@Composable
private fun PermissionPage(
    state: LoginUiState,
    onNotificationChange: (Boolean) -> Unit,
    onActivityChange: (Boolean) -> Unit,
    onContactsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardingLayout(title = "권한 설정", step = 4, onBack = onBack) {
        Text("안전 기능에 필요한 권한", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("필요한 권한만 선택할 수 있어요. 설정에서 언제든 바꿀 수 있습니다.", fontSize = 13.sp, color = Muted)
        Spacer(Modifier.height(22.dp))
        PermissionRow("⌖", "위치", "현재 위치와 안전한 경로 안내", true, true, {})
        PermissionRow("♧", "알림", "위험 알림과 보호자 메시지", state.notificationPermission, false, onNotificationChange)
        PermissionRow("↗", "신체 활동", "이동 상태를 활용한 안전 확인", state.activityPermission, false, onActivityChange)
        PermissionRow("♙", "연락처", "긴급 상황에서 연락할 사람 연결", state.contactsPermission, false, onContactsChange)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().background(Soft, RoundedCornerShape(6.dp)).padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("ⓘ", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text("선택 권한을 허용하지 않아도 기본 지도와 경로 검색은 사용할 수 있어요.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("선택한 권한으로 시작", true, onStart)
        if (state.isLoading) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp).align(Alignment.CenterHorizontally))
        }
        state.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = ErrorRed, fontSize = 11.sp)
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("나중에 설정", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PermissionRow(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    required: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(Soft, CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, color = Muted, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(if (required) "필수" else "선택", color = Muted, fontSize = 9.sp)
            }
            Text(description, color = Muted, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            enabled = !required,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Ink,
                disabledCheckedThumbColor = Color.White,
                disabledCheckedTrackColor = Ink,
            ),
            modifier = Modifier.size(width = 46.dp, height = 28.dp),
        )
    }
}

@Composable
private fun OnboardingLayout(
    title: String,
    step: Int,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", color = Ink, fontSize = 32.sp,
                modifier = Modifier.width(36.dp).clickable(onClick = onBack),
            )
            Text(
                title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(36.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(4) { index ->
                Box(
                    Modifier.weight(1f).height(3.dp)
                        .background(if (index < step) Ink else Line, RoundedCornerShape(3.dp)),
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        content()
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false,
) {
    Column {
        Text(label, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFFA5AAA7), fontSize = 13.sp) },
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            shape = RoundedCornerShape(5.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink,
                unfocusedBorderColor = Color(0xFFBEC3C0),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(5.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFD7DAD8),
            disabledContentColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginPreview() {
    LoginScreen(
        state = LoginUiState(),
        onLoginEmailChange = {},
        onLoginPasswordChange = {},
        onNameChange = {},
        onSignUpEmailChange = {},
        onSignUpPasswordChange = {},
        onPhoneNumberChange = {},
        onGuardianNameChange = {},
        onGuardianPhoneChange = {},
        onTermsAgreementChange = {},
        onPrivacyAgreementChange = {},
        onLocationAgreementChange = {},
        onAllAgreementsChange = {},
        onUserTypeChange = {},
        onNotificationPermissionChange = {},
        onActivityPermissionChange = {},
        onContactsPermissionChange = {},
        onOpenSignUp = {},
        onContinueToTerms = {},
        onContinueToUserType = {},
        onContinueToPermissions = {},
        onBack = {},
        onLogin = {},
        onContinueAsGuest = {},
        onStart = {},
        onSkipPermissions = {},
    )
}
