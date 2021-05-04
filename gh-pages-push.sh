git pull origin docs
rm -rf docs/
mdbook build design -d ../docs
git add docs/
git commit -m "Update docs"
