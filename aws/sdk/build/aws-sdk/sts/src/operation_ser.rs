// Code generated by software.amazon.smithy.rust.codegen.smithy-rs. DO NOT EDIT.
pub fn serialize_operation_assume_role(
    input: &crate::input::AssumeRoleInput,
) -> Result<smithy_http::body::SdkBody, serde_json::error::Error> {
    let mut out = String::new();
    #[allow(unused_mut)]
    let mut writer = smithy_query::QueryWriter::new(&mut out, "AssumeRole", "2011-06-15");
    #[allow(unused_mut)]
    let mut scope_1 = writer.prefix("RoleArn");
    if let Some(var_2) = &input.role_arn {
        scope_1.string(var_2);
    }
    #[allow(unused_mut)]
    let mut scope_3 = writer.prefix("RoleSessionName");
    if let Some(var_4) = &input.role_session_name {
        scope_3.string(var_4);
    }
    #[allow(unused_mut)]
    let mut scope_5 = writer.prefix("PolicyArns");
    if let Some(var_6) = &input.policy_arns {
        let mut list_8 = scope_5.start_list(false, None);
        for item_7 in var_6 {
            #[allow(unused_mut)]
            let mut entry_9 = list_8.entry();
            crate::query_ser::serialize_structure_policy_descriptor_type(entry_9, item_7);
        }
        list_8.finish();
    }
    #[allow(unused_mut)]
    let mut scope_10 = writer.prefix("Policy");
    if let Some(var_11) = &input.policy {
        scope_10.string(var_11);
    }
    #[allow(unused_mut)]
    let mut scope_12 = writer.prefix("DurationSeconds");
    if let Some(var_13) = &input.duration_seconds {
        scope_12.number(
            #[allow(clippy::useless_conversion)]
            smithy_types::Number::NegInt((*var_13).into()),
        );
    }
    #[allow(unused_mut)]
    let mut scope_14 = writer.prefix("Tags");
    if let Some(var_15) = &input.tags {
        let mut list_17 = scope_14.start_list(false, None);
        for item_16 in var_15 {
            #[allow(unused_mut)]
            let mut entry_18 = list_17.entry();
            crate::query_ser::serialize_structure_tag(entry_18, item_16);
        }
        list_17.finish();
    }
    #[allow(unused_mut)]
    let mut scope_19 = writer.prefix("TransitiveTagKeys");
    if let Some(var_20) = &input.transitive_tag_keys {
        let mut list_22 = scope_19.start_list(false, None);
        for item_21 in var_20 {
            #[allow(unused_mut)]
            let mut entry_23 = list_22.entry();
            entry_23.string(item_21);
        }
        list_22.finish();
    }
    #[allow(unused_mut)]
    let mut scope_24 = writer.prefix("ExternalId");
    if let Some(var_25) = &input.external_id {
        scope_24.string(var_25);
    }
    #[allow(unused_mut)]
    let mut scope_26 = writer.prefix("SerialNumber");
    if let Some(var_27) = &input.serial_number {
        scope_26.string(var_27);
    }
    #[allow(unused_mut)]
    let mut scope_28 = writer.prefix("TokenCode");
    if let Some(var_29) = &input.token_code {
        scope_28.string(var_29);
    }
    #[allow(unused_mut)]
    let mut scope_30 = writer.prefix("SourceIdentity");
    if let Some(var_31) = &input.source_identity {
        scope_30.string(var_31);
    }
    writer.finish();
    Ok(smithy_http::body::SdkBody::from(out))
}

pub fn serialize_operation_assume_role_with_saml(
    input: &crate::input::AssumeRoleWithSAMLInput,
) -> Result<smithy_http::body::SdkBody, serde_json::error::Error> {
    let mut out = String::new();
    #[allow(unused_mut)]
    let mut writer = smithy_query::QueryWriter::new(&mut out, "AssumeRoleWithSAML", "2011-06-15");
    #[allow(unused_mut)]
    let mut scope_32 = writer.prefix("RoleArn");
    if let Some(var_33) = &input.role_arn {
        scope_32.string(var_33);
    }
    #[allow(unused_mut)]
    let mut scope_34 = writer.prefix("PrincipalArn");
    if let Some(var_35) = &input.principal_arn {
        scope_34.string(var_35);
    }
    #[allow(unused_mut)]
    let mut scope_36 = writer.prefix("SAMLAssertion");
    if let Some(var_37) = &input.saml_assertion {
        scope_36.string(var_37);
    }
    #[allow(unused_mut)]
    let mut scope_38 = writer.prefix("PolicyArns");
    if let Some(var_39) = &input.policy_arns {
        let mut list_41 = scope_38.start_list(false, None);
        for item_40 in var_39 {
            #[allow(unused_mut)]
            let mut entry_42 = list_41.entry();
            crate::query_ser::serialize_structure_policy_descriptor_type(entry_42, item_40);
        }
        list_41.finish();
    }
    #[allow(unused_mut)]
    let mut scope_43 = writer.prefix("Policy");
    if let Some(var_44) = &input.policy {
        scope_43.string(var_44);
    }
    #[allow(unused_mut)]
    let mut scope_45 = writer.prefix("DurationSeconds");
    if let Some(var_46) = &input.duration_seconds {
        scope_45.number(
            #[allow(clippy::useless_conversion)]
            smithy_types::Number::NegInt((*var_46).into()),
        );
    }
    writer.finish();
    Ok(smithy_http::body::SdkBody::from(out))
}

pub fn serialize_operation_assume_role_with_web_identity(
    input: &crate::input::AssumeRoleWithWebIdentityInput,
) -> Result<smithy_http::body::SdkBody, serde_json::error::Error> {
    let mut out = String::new();
    #[allow(unused_mut)]
    let mut writer =
        smithy_query::QueryWriter::new(&mut out, "AssumeRoleWithWebIdentity", "2011-06-15");
    #[allow(unused_mut)]
    let mut scope_47 = writer.prefix("RoleArn");
    if let Some(var_48) = &input.role_arn {
        scope_47.string(var_48);
    }
    #[allow(unused_mut)]
    let mut scope_49 = writer.prefix("RoleSessionName");
    if let Some(var_50) = &input.role_session_name {
        scope_49.string(var_50);
    }
    #[allow(unused_mut)]
    let mut scope_51 = writer.prefix("WebIdentityToken");
    if let Some(var_52) = &input.web_identity_token {
        scope_51.string(var_52);
    }
    #[allow(unused_mut)]
    let mut scope_53 = writer.prefix("ProviderId");
    if let Some(var_54) = &input.provider_id {
        scope_53.string(var_54);
    }
    #[allow(unused_mut)]
    let mut scope_55 = writer.prefix("PolicyArns");
    if let Some(var_56) = &input.policy_arns {
        let mut list_58 = scope_55.start_list(false, None);
        for item_57 in var_56 {
            #[allow(unused_mut)]
            let mut entry_59 = list_58.entry();
            crate::query_ser::serialize_structure_policy_descriptor_type(entry_59, item_57);
        }
        list_58.finish();
    }
    #[allow(unused_mut)]
    let mut scope_60 = writer.prefix("Policy");
    if let Some(var_61) = &input.policy {
        scope_60.string(var_61);
    }
    #[allow(unused_mut)]
    let mut scope_62 = writer.prefix("DurationSeconds");
    if let Some(var_63) = &input.duration_seconds {
        scope_62.number(
            #[allow(clippy::useless_conversion)]
            smithy_types::Number::NegInt((*var_63).into()),
        );
    }
    writer.finish();
    Ok(smithy_http::body::SdkBody::from(out))
}

pub fn serialize_operation_decode_authorization_message(
    input: &crate::input::DecodeAuthorizationMessageInput,
) -> Result<smithy_http::body::SdkBody, serde_json::error::Error> {
    let mut out = String::new();
    #[allow(unused_mut)]
    let mut writer =
        smithy_query::QueryWriter::new(&mut out, "DecodeAuthorizationMessage", "2011-06-15");
    #[allow(unused_mut)]
    let mut scope_64 = writer.prefix("EncodedMessage");
    if let Some(var_65) = &input.encoded_message {
        scope_64.string(var_65);
    }
    writer.finish();
    Ok(smithy_http::body::SdkBody::from(out))
}

pub fn serialize_operation_get_access_key_info(
    input: &crate::input::GetAccessKeyInfoInput,
) -> Result<smithy_http::body::SdkBody, serde_json::error::Error> {
    let mut out = String::new();
    #[allow(unused_mut)]
    let mut writer = smithy_query::QueryWriter::new(&mut out, "GetAccessKeyInfo", "2011-06-15");
    #[allow(unused_mut)]
    let mut scope_66 = writer.prefix("AccessKeyId");
    if let Some(var_67) = &input.access_key_id {
        scope_66.string(var_67);
    }
    writer.finish();
    Ok(smithy_http::body::SdkBody::from(out))
}

pub fn serialize_operation_get_caller_identity(
    input: &crate::input::GetCallerIdentityInput,
) -> Result<smithy_http::body::SdkBody, serde_json::error::Error> {
    let _ = input;
    let mut out = String::new();
    #[allow(unused_mut)]
    let mut writer = smithy_query::QueryWriter::new(&mut out, "GetCallerIdentity", "2011-06-15");
    writer.finish();
    Ok(smithy_http::body::SdkBody::from(out))
}

pub fn serialize_operation_get_federation_token(
    input: &crate::input::GetFederationTokenInput,
) -> Result<smithy_http::body::SdkBody, serde_json::error::Error> {
    let mut out = String::new();
    #[allow(unused_mut)]
    let mut writer = smithy_query::QueryWriter::new(&mut out, "GetFederationToken", "2011-06-15");
    #[allow(unused_mut)]
    let mut scope_68 = writer.prefix("Name");
    if let Some(var_69) = &input.name {
        scope_68.string(var_69);
    }
    #[allow(unused_mut)]
    let mut scope_70 = writer.prefix("Policy");
    if let Some(var_71) = &input.policy {
        scope_70.string(var_71);
    }
    #[allow(unused_mut)]
    let mut scope_72 = writer.prefix("PolicyArns");
    if let Some(var_73) = &input.policy_arns {
        let mut list_75 = scope_72.start_list(false, None);
        for item_74 in var_73 {
            #[allow(unused_mut)]
            let mut entry_76 = list_75.entry();
            crate::query_ser::serialize_structure_policy_descriptor_type(entry_76, item_74);
        }
        list_75.finish();
    }
    #[allow(unused_mut)]
    let mut scope_77 = writer.prefix("DurationSeconds");
    if let Some(var_78) = &input.duration_seconds {
        scope_77.number(
            #[allow(clippy::useless_conversion)]
            smithy_types::Number::NegInt((*var_78).into()),
        );
    }
    #[allow(unused_mut)]
    let mut scope_79 = writer.prefix("Tags");
    if let Some(var_80) = &input.tags {
        let mut list_82 = scope_79.start_list(false, None);
        for item_81 in var_80 {
            #[allow(unused_mut)]
            let mut entry_83 = list_82.entry();
            crate::query_ser::serialize_structure_tag(entry_83, item_81);
        }
        list_82.finish();
    }
    writer.finish();
    Ok(smithy_http::body::SdkBody::from(out))
}

pub fn serialize_operation_get_session_token(
    input: &crate::input::GetSessionTokenInput,
) -> Result<smithy_http::body::SdkBody, serde_json::error::Error> {
    let mut out = String::new();
    #[allow(unused_mut)]
    let mut writer = smithy_query::QueryWriter::new(&mut out, "GetSessionToken", "2011-06-15");
    #[allow(unused_mut)]
    let mut scope_84 = writer.prefix("DurationSeconds");
    if let Some(var_85) = &input.duration_seconds {
        scope_84.number(
            #[allow(clippy::useless_conversion)]
            smithy_types::Number::NegInt((*var_85).into()),
        );
    }
    #[allow(unused_mut)]
    let mut scope_86 = writer.prefix("SerialNumber");
    if let Some(var_87) = &input.serial_number {
        scope_86.string(var_87);
    }
    #[allow(unused_mut)]
    let mut scope_88 = writer.prefix("TokenCode");
    if let Some(var_89) = &input.token_code {
        scope_88.string(var_89);
    }
    writer.finish();
    Ok(smithy_http::body::SdkBody::from(out))
}
