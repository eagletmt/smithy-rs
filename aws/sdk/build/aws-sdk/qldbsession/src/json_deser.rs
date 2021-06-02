// Code generated by software.amazon.smithy.rust.codegen.smithy-rs. DO NOT EDIT.
pub fn send_command_deser_operation(
    inp: &[u8],
    mut builder: crate::output::send_command_output::Builder,
) -> Result<crate::output::send_command_output::Builder, serde_json::Error> {
    let parsed_body: crate::serializer::SendCommandOutputBody = if inp.is_empty() {
        // To enable JSON parsing to succeed, replace an empty body
        // with an empty JSON body. If a member was required, it will fail slightly later
        // during the operation construction phase when a required field was missing.
        serde_json::from_slice(b"{}")?
    } else {
        serde_json::from_slice(inp)?
    };
    builder = builder.set_start_session(parsed_body.start_session);
    builder = builder.set_start_transaction(parsed_body.start_transaction);
    builder = builder.set_end_session(parsed_body.end_session);
    builder = builder.set_commit_transaction(parsed_body.commit_transaction);
    builder = builder.set_abort_transaction(parsed_body.abort_transaction);
    builder = builder.set_execute_statement(parsed_body.execute_statement);
    builder = builder.set_fetch_page(parsed_body.fetch_page);
    Ok(builder)
}
