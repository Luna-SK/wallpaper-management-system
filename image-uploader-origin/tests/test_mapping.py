from image_uploader.mapping import FOLDER_TAGS


def test_mapping_matches_legacy_script() -> None:
    expected = {
        "无疵点": "0",
        "破洞": "1",
        "水渍": "2",
        "油渍": "2",
        "污渍": "2",
        "三丝": "3",
        "结头": "4",
        "花板跳": "5",
        "百脚": "6",
        "毛粒": "7",
        "粗经": "8",
        "松经": "9",
        "断经": "10",
        "吊经": "11",
        "粗维": "12",
        "纬缩": "13",
        "浆斑": "14",
        "整经结": "15",
        "星跳": "16",
        "跳花": "16",
        "断氨纶": "17",
        "稀密档": "18",
        "浪纹档": "18",
        "色差档": "18",
        "磨痕": "19",
        "轧痕": "19",
        "修痕": "19",
        "烧毛痕": "19",
        "死皱": "20",
        "云织": "20",
        "双纬": "20",
        "双经": "20",
        "跳纱": "20",
        "筘路": "20",
        "纬纱不良": "20",
    }
    actual = {tag: item.folder for item in FOLDER_TAGS for tag in item.tag_names}

    assert actual == expected
    assert len(actual) == 35
