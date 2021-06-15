#  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
#  SPDX-License-Identifier: Apache-2.0.
import json
import re
from typing import NamedTuple, Dict, Any, Optional, List
from urllib.parse import urlparse, urlunparse


class S3Request(NamedTuple):
    region: str
    bucket: str
    address_style: str
    dualstack: bool
    accelerate: bool
    use_arn_region: bool


class MatchValue(NamedTuple):
    uri_template: Dict[str, Any]
    bucket_regex: str
    header_template: Dict[str, Any]
    credential_scope: Dict[str, str]
    remove_bucket_from_path: bool
    region_match_regex: Optional[str]


class MatchRow(NamedTuple):
    key: Dict[str, Any]
    value: Optional[MatchValue]
    error: Optional[str]


class MatchResult(NamedTuple):
    uri: str
    credential_scope: Dict[str, Any]


class MatchTable:
    def __init__(self, table: List[MatchRow]):
        self.table = table

    @classmethod
    def parse(cls, f):
        data = json.load(f)
        table = []
        for row in data:
            key = row['key']
            value_data = row['value']
            if 'Ok' in value_data:
                error = None
                value = MatchValue(**value_data['Ok'])
            else:
                error = value_data['error']
                value = None
            match_row = MatchRow(key=key, value=value, error=error)
            table.append(match_row)
        MatchTable(table)

    def set_endpoint(self, uri: str, req: S3Request) -> MatchResult:
        """
        Set the endpoint for uri given the settings from req:

        This is the main entrypoint for S3 customizations

        :param uri:
        :param req:
        :return:
        """
        for row in self.table:
            if match(req, row.key):
                if row.error:
                    raise Exception(row.error)
                else:
                    new_uri = apply(uri, req, row.value)
                    return MatchResult(new_uri, row.value.credential_scope)
        raise Exception('No rows matched')


exact_keys = ['address_style', 'dualstack', 'accelerate', 'use_arn_region']


def match(req: S3Request, row: Dict[str, Any]) -> bool:
    for key in exact_keys:
        if row.get(key) is not None:
            if row[key] != getattr(req, key):
                return False

    if row.get('region_regex') is not None:
        if not re.match(row['region_regex'], req.region):
            return False

    if row.get('bucket_regex') is not None:
        if not re.match(row['region_regex'], req.region):
            return False
    return True


def apply(uri: str, req: S3Request, match_value: MatchValue):
    parsed = urlparse(uri)

    region_match = match_value.region_match_regex
    if region_match is not None:
        if not re.match(region_match, req.region):
            raise Exception('Invalid region')

    # if this is virtual address compatible, remove the bucket from the path
    if match_value.remove_bucket_from_path:
        if not parsed.path.startswith(req.bucket):
            raise Exception('invalid uri')
        parsed = parsed._replace(path=parsed.path[len(req.bucket):])

    template: str = match_value.uri_template['template']
    match_groups = re.search(match_value.bucket_regex, req.bucket)
    assert match_groups is not None
    for key in match_value.uri_template['keys']:
        if key == 'region':
            template = template.replace('{region}', req.region)
        else:
            capture_group = int(key[len('bucket:'):])
            template = template.replace('{' + key + '}', match_groups.group(capture_group))

    parsed_template = urlparse(template)
    new_uri = (parsed_template.scheme, parsed_template.netloc, parsed.path, parsed.params, parsed.query)
    return urlunparse(new_uri)
