package com.safewalk.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BrandGreen = Color(0xFF167D5B)
private val ScreenBackground = Color(0xFFF4F8F6)

@Composable
fun LoginScreen(
    state: LoginUiState,
    onUserIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUserTypeChange: (UserType) -> Unit,
    onLogin: () -> Unit,
    onContinueAsGuest: () -> Unit,
) {
    MaterialTheme {
        Surface(color = ScreenBackground, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                BrandHeader()
                Spacer(Modifier.height(40.dp))

                Text(
                    text = "사용자 유형",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF33443E),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UserTypeOption(
                        label = "성인",
                        selected = state.userType == UserType.ADULT,
                        onClick = { onUserTypeChange(UserType.ADULT) },
                        modifier = Modifier.weight(1f),
                    )
                    UserTypeOption(
                        label = "미성년자",
                        selected = state.userType == UserType.MINOR,
                        onClick = { onUserTypeChange(UserType.MINOR) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = state.userId,
                    onValueChange = onUserIdChange,
                    label = { Text("아이디") },
                    placeholder = { Text("아이디를 입력해 주세요") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("비밀번호") },
                    placeholder = { Text("비밀번호를 입력해 주세요") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onLogin,
                    enabled = state.canLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text("로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onContinueAsGuest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("비로그인으로 시작하기", color = BrandGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .background(BrandGreen, CircleShape)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            Text("길", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("GODGILL", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF183C30))
            Text("안전한 귀갓길을 시작하세요", fontSize = 14.sp, color = Color(0xFF65756F))
        }
    }
}

@Composable
private fun UserTypeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) Color(0xFFE5F3ED) else Color.White,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (selected) 0.dp else 1.dp,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = BrandGreen),
            )
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    LoginScreen(
        state = LoginUiState(userId = "godgill_user"),
        onUserIdChange = {},
        onPasswordChange = {},
        onUserTypeChange = {},
        onLogin = {},
        onContinueAsGuest = {},
    )
}
