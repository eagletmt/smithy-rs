// Code generated by software.amazon.smithy.rust.codegen.smithy-rs. DO NOT EDIT.
use smithy_types::Blob;
use smithy_types::Instant;
#[non_exhaustive]
#[derive(::std::clone::Clone, ::std::cmp::PartialEq, ::std::fmt::Debug)]
pub enum MyUnion {
    BlobValue(Blob),
    BooleanValue(bool),
    EnumValue(FooEnum),
    ListValue(::std::vec::Vec<::std::string::String>),
    MapValue(::std::collections::HashMap<::std::string::String, ::std::string::String>),
    NumberValue(i32),
    StringValue(::std::string::String),
    StructureValue(GreetingStruct),
    TimestampValue(Instant),
}

#[non_exhaustive]
#[derive(::std::clone::Clone, ::std::cmp::PartialEq, ::std::fmt::Debug)]
pub struct GreetingStruct {
    pub hi: ::std::option::Option<::std::string::String>,
}
/// See [`GreetingStruct`](crate::model::GreetingStruct)
pub mod greeting_struct {

    use crate::model::GreetingStruct;
    /// A builder for [`GreetingStruct`](crate::model::GreetingStruct)
    #[non_exhaustive]
    #[derive(Debug, Clone, Default)]
    pub struct Builder {
        hi: ::std::option::Option<::std::string::String>,
    }
    impl Builder {
        pub fn hi(mut self, inp: impl Into<::std::string::String>) -> Self {
            self.hi = Some(inp.into());
            self
        }
        /// Consumes the builder and constructs a [`GreetingStruct`](crate::model::GreetingStruct)
        pub fn build(self) -> GreetingStruct {
            GreetingStruct { hi: self.hi }
        }
    }
}
impl GreetingStruct {
    /// Creates a new builder-style object to manufacture [`GreetingStruct`](crate::model::GreetingStruct)
    pub fn builder() -> crate::model::greeting_struct::Builder {
        crate::model::greeting_struct::Builder::default()
    }
}

#[non_exhaustive]
#[derive(
    ::std::clone::Clone,
    ::std::cmp::Eq,
    ::std::cmp::Ord,
    ::std::cmp::PartialEq,
    ::std::cmp::PartialOrd,
    ::std::fmt::Debug,
    ::std::hash::Hash,
)]
pub enum FooEnum {
    Zero,
    One,
    Bar,
    Baz,
    Foo,
    Unknown(String),
}
impl<T> ::std::convert::From<T> for FooEnum
where
    T: ::std::convert::AsRef<str>,
{
    fn from(s: T) -> Self {
        match s.as_ref() {
            "0" => FooEnum::Zero,
            "1" => FooEnum::One,
            "Bar" => FooEnum::Bar,
            "Baz" => FooEnum::Baz,
            "Foo" => FooEnum::Foo,
            other => FooEnum::Unknown(other.to_owned()),
        }
    }
}
impl FooEnum {
    pub fn as_str(&self) -> &str {
        match self {
            FooEnum::Zero => "0",
            FooEnum::One => "1",
            FooEnum::Bar => "Bar",
            FooEnum::Baz => "Baz",
            FooEnum::Foo => "Foo",
            FooEnum::Unknown(s) => s.as_ref(),
        }
    }
}

#[non_exhaustive]
#[derive(::std::clone::Clone, ::std::cmp::PartialEq, ::std::fmt::Debug)]
pub struct StructureListMember {
    pub a: ::std::option::Option<::std::string::String>,
    pub b: ::std::option::Option<::std::string::String>,
}
/// See [`StructureListMember`](crate::model::StructureListMember)
pub mod structure_list_member {

    use crate::model::StructureListMember;
    /// A builder for [`StructureListMember`](crate::model::StructureListMember)
    #[non_exhaustive]
    #[derive(Debug, Clone, Default)]
    pub struct Builder {
        a: ::std::option::Option<::std::string::String>,
        b: ::std::option::Option<::std::string::String>,
    }
    impl Builder {
        pub fn a(mut self, inp: impl Into<::std::string::String>) -> Self {
            self.a = Some(inp.into());
            self
        }
        pub fn b(mut self, inp: impl Into<::std::string::String>) -> Self {
            self.b = Some(inp.into());
            self
        }
        /// Consumes the builder and constructs a [`StructureListMember`](crate::model::StructureListMember)
        pub fn build(self) -> StructureListMember {
            StructureListMember {
                a: self.a,
                b: self.b,
            }
        }
    }
}
impl StructureListMember {
    /// Creates a new builder-style object to manufacture [`StructureListMember`](crate::model::StructureListMember)
    pub fn builder() -> crate::model::structure_list_member::Builder {
        crate::model::structure_list_member::Builder::default()
    }
}

#[non_exhaustive]
#[derive(::std::clone::Clone, ::std::cmp::PartialEq, ::std::fmt::Debug)]
pub struct RecursiveShapesInputOutputNested1 {
    pub foo: ::std::option::Option<::std::string::String>,
    pub nested: ::std::option::Option<::std::boxed::Box<RecursiveShapesInputOutputNested2>>,
}
/// See [`RecursiveShapesInputOutputNested1`](crate::model::RecursiveShapesInputOutputNested1)
pub mod recursive_shapes_input_output_nested1 {

    use crate::model::RecursiveShapesInputOutputNested1;
    use crate::model::RecursiveShapesInputOutputNested2;
    /// A builder for [`RecursiveShapesInputOutputNested1`](crate::model::RecursiveShapesInputOutputNested1)
    #[non_exhaustive]
    #[derive(Debug, Clone, Default)]
    pub struct Builder {
        foo: ::std::option::Option<::std::string::String>,
        nested: ::std::option::Option<::std::boxed::Box<RecursiveShapesInputOutputNested2>>,
    }
    impl Builder {
        pub fn foo(mut self, inp: impl Into<::std::string::String>) -> Self {
            self.foo = Some(inp.into());
            self
        }
        pub fn nested(
            mut self,
            inp: impl Into<::std::boxed::Box<RecursiveShapesInputOutputNested2>>,
        ) -> Self {
            self.nested = Some(inp.into());
            self
        }
        /// Consumes the builder and constructs a [`RecursiveShapesInputOutputNested1`](crate::model::RecursiveShapesInputOutputNested1)
        pub fn build(self) -> RecursiveShapesInputOutputNested1 {
            RecursiveShapesInputOutputNested1 {
                foo: self.foo,
                nested: self.nested,
            }
        }
    }
}
impl RecursiveShapesInputOutputNested1 {
    /// Creates a new builder-style object to manufacture [`RecursiveShapesInputOutputNested1`](crate::model::RecursiveShapesInputOutputNested1)
    pub fn builder() -> crate::model::recursive_shapes_input_output_nested1::Builder {
        crate::model::recursive_shapes_input_output_nested1::Builder::default()
    }
}

#[non_exhaustive]
#[derive(::std::clone::Clone, ::std::cmp::PartialEq, ::std::fmt::Debug)]
pub struct RecursiveShapesInputOutputNested2 {
    pub bar: ::std::option::Option<::std::string::String>,
    pub recursive_member: ::std::option::Option<RecursiveShapesInputOutputNested1>,
}
/// See [`RecursiveShapesInputOutputNested2`](crate::model::RecursiveShapesInputOutputNested2)
pub mod recursive_shapes_input_output_nested2 {

    use crate::model::RecursiveShapesInputOutputNested1;
    use crate::model::RecursiveShapesInputOutputNested2;
    /// A builder for [`RecursiveShapesInputOutputNested2`](crate::model::RecursiveShapesInputOutputNested2)
    #[non_exhaustive]
    #[derive(Debug, Clone, Default)]
    pub struct Builder {
        bar: ::std::option::Option<::std::string::String>,
        recursive_member: ::std::option::Option<RecursiveShapesInputOutputNested1>,
    }
    impl Builder {
        pub fn bar(mut self, inp: impl Into<::std::string::String>) -> Self {
            self.bar = Some(inp.into());
            self
        }
        pub fn recursive_member(mut self, inp: RecursiveShapesInputOutputNested1) -> Self {
            self.recursive_member = Some(inp);
            self
        }
        /// Consumes the builder and constructs a [`RecursiveShapesInputOutputNested2`](crate::model::RecursiveShapesInputOutputNested2)
        pub fn build(self) -> RecursiveShapesInputOutputNested2 {
            RecursiveShapesInputOutputNested2 {
                bar: self.bar,
                recursive_member: self.recursive_member,
            }
        }
    }
}
impl RecursiveShapesInputOutputNested2 {
    /// Creates a new builder-style object to manufacture [`RecursiveShapesInputOutputNested2`](crate::model::RecursiveShapesInputOutputNested2)
    pub fn builder() -> crate::model::recursive_shapes_input_output_nested2::Builder {
        crate::model::recursive_shapes_input_output_nested2::Builder::default()
    }
}

#[non_exhaustive]
#[derive(::std::clone::Clone, ::std::cmp::PartialEq, ::std::fmt::Debug)]
pub struct ComplexNestedErrorData {
    pub foo: ::std::option::Option<::std::string::String>,
}
/// See [`ComplexNestedErrorData`](crate::model::ComplexNestedErrorData)
pub mod complex_nested_error_data {

    use crate::model::ComplexNestedErrorData;
    /// A builder for [`ComplexNestedErrorData`](crate::model::ComplexNestedErrorData)
    #[non_exhaustive]
    #[derive(Debug, Clone, Default)]
    pub struct Builder {
        foo: ::std::option::Option<::std::string::String>,
    }
    impl Builder {
        pub fn foo(mut self, inp: impl Into<::std::string::String>) -> Self {
            self.foo = Some(inp.into());
            self
        }
        /// Consumes the builder and constructs a [`ComplexNestedErrorData`](crate::model::ComplexNestedErrorData)
        pub fn build(self) -> ComplexNestedErrorData {
            ComplexNestedErrorData { foo: self.foo }
        }
    }
}
impl ComplexNestedErrorData {
    /// Creates a new builder-style object to manufacture [`ComplexNestedErrorData`](crate::model::ComplexNestedErrorData)
    pub fn builder() -> crate::model::complex_nested_error_data::Builder {
        crate::model::complex_nested_error_data::Builder::default()
    }
}

#[non_exhaustive]
#[derive(::std::clone::Clone, ::std::cmp::PartialEq, ::std::fmt::Debug)]
pub struct NestedPayload {
    pub greeting: ::std::option::Option<::std::string::String>,
    pub name: ::std::option::Option<::std::string::String>,
}
/// See [`NestedPayload`](crate::model::NestedPayload)
pub mod nested_payload {

    use crate::model::NestedPayload;
    /// A builder for [`NestedPayload`](crate::model::NestedPayload)
    #[non_exhaustive]
    #[derive(Debug, Clone, Default)]
    pub struct Builder {
        greeting: ::std::option::Option<::std::string::String>,
        name: ::std::option::Option<::std::string::String>,
    }
    impl Builder {
        pub fn greeting(mut self, inp: impl Into<::std::string::String>) -> Self {
            self.greeting = Some(inp.into());
            self
        }
        pub fn name(mut self, inp: impl Into<::std::string::String>) -> Self {
            self.name = Some(inp.into());
            self
        }
        /// Consumes the builder and constructs a [`NestedPayload`](crate::model::NestedPayload)
        pub fn build(self) -> NestedPayload {
            NestedPayload {
                greeting: self.greeting,
                name: self.name,
            }
        }
    }
}
impl NestedPayload {
    /// Creates a new builder-style object to manufacture [`NestedPayload`](crate::model::NestedPayload)
    pub fn builder() -> crate::model::nested_payload::Builder {
        crate::model::nested_payload::Builder::default()
    }
}
