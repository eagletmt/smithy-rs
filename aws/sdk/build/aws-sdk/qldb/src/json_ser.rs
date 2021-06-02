// Code generated by software.amazon.smithy.rust.codegen.smithy-rs. DO NOT EDIT.
pub fn serialize_structure_create_ledger_input(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::input::CreateLedgerInput,
) {
    if let Some(var_1) = &input.name {
        object.key("Name").string(var_1);
    }
    if let Some(var_2) = &input.tags {
        let mut object_3 = object.key("Tags").start_object();
        for (key_4, value_5) in var_2 {
            if let Some(var_6) = value_5 {
                object_3.key(key_4).string(var_6);
            } else {
                object_3.key(key_4).null();
            }
        }
        object_3.finish();
    }
    if let Some(var_7) = &input.permissions_mode {
        object.key("PermissionsMode").string(var_7.as_str());
    }
    if let Some(var_8) = &input.deletion_protection {
        object.key("DeletionProtection").boolean(*var_8);
    }
}

pub fn serialize_structure_export_journal_to_s3_input(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::input::ExportJournalToS3Input,
) {
    if let Some(var_9) = &input.inclusive_start_time {
        object
            .key("InclusiveStartTime")
            .instant(var_9, smithy_types::instant::Format::EpochSeconds);
    }
    if let Some(var_10) = &input.exclusive_end_time {
        object
            .key("ExclusiveEndTime")
            .instant(var_10, smithy_types::instant::Format::EpochSeconds);
    }
    if let Some(var_11) = &input.s3_export_configuration {
        let mut object_12 = object.key("S3ExportConfiguration").start_object();
        crate::json_ser::serialize_structure_s3_export_configuration(&mut object_12, var_11);
        object_12.finish();
    }
    if let Some(var_13) = &input.role_arn {
        object.key("RoleArn").string(var_13);
    }
}

pub fn serialize_structure_get_block_input(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::input::GetBlockInput,
) {
    if let Some(var_14) = &input.block_address {
        let mut object_15 = object.key("BlockAddress").start_object();
        crate::json_ser::serialize_structure_value_holder(&mut object_15, var_14);
        object_15.finish();
    }
    if let Some(var_16) = &input.digest_tip_address {
        let mut object_17 = object.key("DigestTipAddress").start_object();
        crate::json_ser::serialize_structure_value_holder(&mut object_17, var_16);
        object_17.finish();
    }
}

pub fn serialize_structure_get_revision_input(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::input::GetRevisionInput,
) {
    if let Some(var_18) = &input.block_address {
        let mut object_19 = object.key("BlockAddress").start_object();
        crate::json_ser::serialize_structure_value_holder(&mut object_19, var_18);
        object_19.finish();
    }
    if let Some(var_20) = &input.document_id {
        object.key("DocumentId").string(var_20);
    }
    if let Some(var_21) = &input.digest_tip_address {
        let mut object_22 = object.key("DigestTipAddress").start_object();
        crate::json_ser::serialize_structure_value_holder(&mut object_22, var_21);
        object_22.finish();
    }
}

pub fn serialize_structure_stream_journal_to_kinesis_input(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::input::StreamJournalToKinesisInput,
) {
    if let Some(var_23) = &input.role_arn {
        object.key("RoleArn").string(var_23);
    }
    if let Some(var_24) = &input.tags {
        let mut object_25 = object.key("Tags").start_object();
        for (key_26, value_27) in var_24 {
            if let Some(var_28) = value_27 {
                object_25.key(key_26).string(var_28);
            } else {
                object_25.key(key_26).null();
            }
        }
        object_25.finish();
    }
    if let Some(var_29) = &input.inclusive_start_time {
        object
            .key("InclusiveStartTime")
            .instant(var_29, smithy_types::instant::Format::EpochSeconds);
    }
    if let Some(var_30) = &input.exclusive_end_time {
        object
            .key("ExclusiveEndTime")
            .instant(var_30, smithy_types::instant::Format::EpochSeconds);
    }
    if let Some(var_31) = &input.kinesis_configuration {
        let mut object_32 = object.key("KinesisConfiguration").start_object();
        crate::json_ser::serialize_structure_kinesis_configuration(&mut object_32, var_31);
        object_32.finish();
    }
    if let Some(var_33) = &input.stream_name {
        object.key("StreamName").string(var_33);
    }
}

pub fn serialize_structure_tag_resource_input(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::input::TagResourceInput,
) {
    if let Some(var_34) = &input.tags {
        let mut object_35 = object.key("Tags").start_object();
        for (key_36, value_37) in var_34 {
            if let Some(var_38) = value_37 {
                object_35.key(key_36).string(var_38);
            } else {
                object_35.key(key_36).null();
            }
        }
        object_35.finish();
    }
}

pub fn serialize_structure_update_ledger_input(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::input::UpdateLedgerInput,
) {
    if let Some(var_39) = &input.deletion_protection {
        object.key("DeletionProtection").boolean(*var_39);
    }
}

pub fn serialize_structure_s3_export_configuration(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::model::S3ExportConfiguration,
) {
    if let Some(var_40) = &input.bucket {
        object.key("Bucket").string(var_40);
    }
    if let Some(var_41) = &input.prefix {
        object.key("Prefix").string(var_41);
    }
    if let Some(var_42) = &input.encryption_configuration {
        let mut object_43 = object.key("EncryptionConfiguration").start_object();
        crate::json_ser::serialize_structure_s3_encryption_configuration(&mut object_43, var_42);
        object_43.finish();
    }
}

pub fn serialize_structure_value_holder(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::model::ValueHolder,
) {
    if let Some(var_44) = &input.ion_text {
        object.key("IonText").string(var_44);
    }
}

pub fn serialize_structure_kinesis_configuration(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::model::KinesisConfiguration,
) {
    if let Some(var_45) = &input.stream_arn {
        object.key("StreamArn").string(var_45);
    }
    if let Some(var_46) = &input.aggregation_enabled {
        object.key("AggregationEnabled").boolean(*var_46);
    }
}

pub fn serialize_structure_s3_encryption_configuration(
    object: &mut smithy_json::serialize::JsonObjectWriter,
    input: &crate::model::S3EncryptionConfiguration,
) {
    if let Some(var_47) = &input.object_encryption_type {
        object.key("ObjectEncryptionType").string(var_47.as_str());
    }
    if let Some(var_48) = &input.kms_key_arn {
        object.key("KmsKeyArn").string(var_48);
    }
}
